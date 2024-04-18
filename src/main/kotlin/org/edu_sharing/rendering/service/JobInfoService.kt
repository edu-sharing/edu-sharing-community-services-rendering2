package org.edu_sharing.rendering.service

import org.bson.types.ObjectId
import org.edu_sharing.rendering.blobStorage.StorageService
import org.edu_sharing.rendering.config.annotation.ConditionalOnController
import org.edu_sharing.rendering.dto.JobInfoReply
import org.edu_sharing.rendering.dto.JobProgressInfo
import org.edu_sharing.rendering.dto.ObjectLink
import org.edu_sharing.rendering.dto.RenderModules
import org.edu_sharing.rendering.dto.mapper.Mapper
import org.edu_sharing.rendering.entity.JobStatus
import org.edu_sharing.rendering.entity.RenderingJob
import org.edu_sharing.rendering.entity.SubJob
import org.edu_sharing.rendering.exception.EntryNotFoundException
import org.edu_sharing.rendering.logic.ConversionRetrieval
import org.edu_sharing.rendering.repository.mongo.RenderingJobRepository
import org.edu_sharing.rendering.repository.mongo.SubJobRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service

@ConditionalOnController
@Service
class JobInfoService (
    private val jobRepository: RenderingJobRepository,
    private val subJobRepository: SubJobRepository,
    private val mapper: Mapper,
    private val conversionRetrieval: ConversionRetrieval,
    private val storageImplementation: StorageService
    ){
    fun getJobInfo(jobId: String): JobInfoReply {
        val job = jobRepository.findByIdOrNull(ObjectId(jobId)) ?: throw EntryNotFoundException("Invalid jobId: $jobId")
        val module = RenderModules.MOODLE
        if (isMainJobQueuedOrCopying(job)) {
            return JobInfoReply(mutableListOf(JobProgressInfo(status = job.status)), status = job.status, module = module)
        }
        val infoList: MutableList<JobProgressInfo> = mutableListOf()
        job.subJobs.forEach {
            val jobInfo = JobProgressInfo(quality = it.quality, status = it.status)
            when (it.status) {
                JobStatus.QUEUED -> { jobInfo.progress = getQueuePosition(it) }
                JobStatus.PROCESSING -> { jobInfo.progress = it.progress.toLong() }
                JobStatus.FINISHED -> { jobInfo.objectLink = getObjectLink(it, job) }
                JobStatus.FAILED -> {}
            }
            infoList.add(jobInfo)
        }
        return JobInfoReply(infoList, status = job.status, module = module)
    }

    private fun getObjectLink(subJob: SubJob, renderingJob: RenderingJob): ObjectLink {
        if (renderingJob.module == RenderModules.MOODLE) {
            return ObjectLink(link = subJob.message ?: "")
        }
        var cacheObject = mapper.renderingJobToCacheObject(renderingJob)
        cacheObject.quality = subJob.quality
        cacheObject = conversionRetrieval.getCacheObjectWithConvertedMimeType(cacheObject)
        return storageImplementation.getObjectLink(cacheObject)
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