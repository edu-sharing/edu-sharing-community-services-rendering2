package org.edu_sharing.rendering.service

import org.bson.types.ObjectId
import org.edu_sharing.rendering.config.annotation.ConditionalOnController
import org.edu_sharing.rendering.dto.JobInfoReply
import org.edu_sharing.rendering.dto.JobProgressInfo
import org.edu_sharing.rendering.entity.JobStatus
import org.edu_sharing.rendering.entity.RenderingJob
import org.edu_sharing.rendering.entity.SubJob
import org.edu_sharing.rendering.exception.EntryNotFoundException
import org.edu_sharing.rendering.modules.ModuleRegistry
import org.edu_sharing.rendering.repository.mongo.RenderingJobRepository
import org.edu_sharing.rendering.repository.mongo.SubJobRepository
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
        val renderModule = moduleRegistry.getRenderModule(job.module)
        if (isMainJobQueuedOrCopying(job)) {
            return JobInfoReply(mutableListOf(JobProgressInfo(status = job.status)), status = job.status, module = job.module)
        }
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
