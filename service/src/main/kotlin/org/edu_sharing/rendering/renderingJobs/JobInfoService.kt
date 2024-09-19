package org.edu_sharing.rendering.renderingJobs

import org.bson.types.ObjectId
import org.edu_sharing.rendering.config.annotation.ConditionalOnController
import org.edu_sharing.rendering.exception.EntryNotFoundException
import org.edu_sharing.rendering.modules.ModuleRegistry
import org.edu_sharing.rendering.modules.RenderModule
import org.edu_sharing.rendering.renderingJobs.dto.JobInfoReply
import org.edu_sharing.rendering.renderingJobs.dto.JobProgressInfo
import org.edu_sharing.rendering.renderingJobs.entity.JobStatus
import org.edu_sharing.rendering.renderingJobs.entity.RenderingJob
import org.edu_sharing.rendering.renderingJobs.entity.SubJob
import org.edu_sharing.rendering.renderingJobs.repository.RenderingJobRepository
import org.edu_sharing.rendering.renderingJobs.repository.SubJobRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service

@ConditionalOnController
@Service
class JobInfoService (
    private val jobRepository: RenderingJobRepository,
    private val subJobRepository: SubJobRepository,
    private val moduleRegistry: ModuleRegistry
    ){

    // we can't call this inside getJobInfo because we need to check permissions which is done by the surrounding proxy
    fun getRenderingJob(jobId: String) : RenderingJob {
         return jobRepository.findByIdOrNull(ObjectId(jobId)) ?: throw EntryNotFoundException("Invalid jobId: $jobId")
    }

    @PreAuthorize("hasPermission(#job.esObjectId, 'Read')")
    fun getJobInfo(job: RenderingJob): JobInfoReply {
        if (isMainJobQueuedOrCopying(job)) {
            return JobInfoReply(
                jobs = mutableListOf(JobProgressInfo(status = job.status)),
                status = job.status,
                module = job.module
            )
        }
        val renderModule: RenderModule = moduleRegistry.getRenderModule(job.module)
        val infoList: MutableList<JobProgressInfo> = mutableListOf()
        job.subJobs.forEach {
            val jobInfo = JobProgressInfo(quality = it.quality, status = it.status)
            when (it.status) {
                JobStatus.QUEUED -> { jobInfo.progress = getQueuePosition(it) }
                JobStatus.PROCESSING -> { jobInfo.progress = it.progress.toLong() }
                JobStatus.FINISHED -> { jobInfo.objectLink = renderModule.getObjectLinkFromJobData(it, job) }
                JobStatus.FAILED -> {}
            }
            infoList.add(jobInfo)
        }
        return JobInfoReply(infoList, status = job.status, module = job.module)
    }

    private fun getQueuePosition(subJob: SubJob): Long {
        return subJobRepository.countByIdBeforeAndStatusAndRoutingKey(
            subJob.id,
            subJob.status,
            subJob.routingKey
        )
    }

    private fun isMainJobQueuedOrCopying(job: RenderingJob): Boolean {
        val isQueued = job.status == JobStatus.QUEUED
        val isCopying = job.status == JobStatus.PROCESSING && job.subJobs.size == 0
        return isQueued || isCopying
    }
}
