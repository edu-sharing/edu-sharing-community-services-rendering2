package org.edu_sharing.rendering.renderingJob

import org.bson.types.ObjectId
import org.edu_sharing.rendering.core.annotation.ConditionalOnController
import org.edu_sharing.rendering.core.exception.EntryNotFoundException
import org.edu_sharing.rendering.modules.ModuleRegistry
import org.edu_sharing.rendering.modules.RenderModule
import org.edu_sharing.rendering.renderingJob.dto.JobInfoReply
import org.edu_sharing.rendering.renderingJob.dto.JobProgressInfo
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.RenderingJobStatus
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.edu_sharing.rendering.renderingJob.entity.SubJobStatus
import org.edu_sharing.rendering.renderingJob.repository.RenderingJobRepository
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service

@ConditionalOnController
@Service
class JobInfoService(
    private val jobRepository: RenderingJobRepository,
    private val subJobRepository: SubJobRepository,
    private val moduleRegistry: ModuleRegistry
) {

    // we can't call this inside getJobInfo because we need to check permissions which is done by the surrounding proxy
    fun getRenderingJob(jobId: String): RenderingJob {
        return jobRepository.findByIdOrNull(ObjectId(jobId)) ?: throw EntryNotFoundException("Invalid jobId: $jobId")
    }

    @PreAuthorize("hasPermission(#job.esObjectId, 'ReadAll')")
    fun getJobInfo(job: RenderingJob): JobInfoReply {
        if (isMainJobQueuedOrCopying(job)) {
            return JobInfoReply(
                jobs = mutableListOf(JobProgressInfo(status = SubJobStatus.fromRenderingJobStatus(job.status))),
                status = job.status,
                module = job.module,
                userMessage = job.errorMessage
            )
        }
        val renderModule: RenderModule = moduleRegistry.getRenderModule(job.module)

        if (!job.conversionType || job.subJobs.isEmpty()) {
            return getJobInfoForJobWithoutSubJobs(renderingJob = job, renderModule = renderModule)
        }

        val infoList: MutableList<JobProgressInfo> = mutableListOf()
        job.subJobs.forEach {
            val jobInfo = JobProgressInfo(quality = it.quality, status = it.status)
            when (it.status) {
                SubJobStatus.QUEUED -> {
                    jobInfo.progress = getQueuePosition(it)
                }

                SubJobStatus.PROCESSING -> {
                    jobInfo.progress = it.progress.toLong()
                }

                SubJobStatus.FINISHED -> {
                    jobInfo.objectLink = renderModule.getObjectLinkFromJobData(it, job)
                    jobInfo.additionalData = renderModule.getAdditionalData(it, job.repoId)
                }

                SubJobStatus.FAILED -> {
                    jobInfo.publicErrorMessage = it.errorMessage
                }
            }
            if (it.status != SubJobStatus.FAILED) {
                infoList.add(jobInfo)
            }
        }
        return JobInfoReply(infoList, status = job.status, module = job.module, userMessage = job.errorMessage)
    }

    private fun getQueuePosition(subJob: SubJob): Long {
        if (subJob.createdDate == null) {
            throw IllegalArgumentException("createDate not set")
        }

        return subJobRepository.getQueuePosition(
            createDate = subJob.createdDate,
            status = subJob.status,
            routingKey = subJob.routingKey,
            priority = subJob.priority
        ) ?: -1
    }

    private fun isMainJobQueuedOrCopying(job: RenderingJob): Boolean {
        val isQueued = job.status == RenderingJobStatus.QUEUED
        val isCopying = job.status == RenderingJobStatus.PROCESSING && job.subJobs.isEmpty()
        return isQueued || isCopying
    }

    private fun getJobInfoForJobWithoutSubJobs(renderingJob: RenderingJob, renderModule: RenderModule): JobInfoReply {
        val placeHolderSubJob = SubJob(
            routingKey = "",
            parent = renderingJob,
            status = SubJobStatus.fromRenderingJobStatus(renderingJob.status),
        )
        val objectLink = renderModule.getObjectLinkFromJobData(
            subJob = placeHolderSubJob,
            renderingJob = renderingJob
        )
        return JobInfoReply(
            status = renderingJob.status, module = renderingJob.module, jobs = mutableListOf(
                JobProgressInfo(
                    status = SubJobStatus.fromRenderingJobStatus(renderingJob.status),
                    objectLink = objectLink,
                    publicErrorMessage = renderingJob.errorMessage
                )
            )
        )
    }
}
