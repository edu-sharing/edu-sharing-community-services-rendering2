package org.edu_sharing.rendering.service

import org.bson.types.ObjectId
import org.edu_sharing.rendering.blobStorage.StorageService
import org.edu_sharing.rendering.dto.JobInfoReply
import org.edu_sharing.rendering.dto.JobProgressInfo
import org.edu_sharing.rendering.dto.RenderModules
import org.edu_sharing.rendering.dto.mapper.Mapper
import org.edu_sharing.rendering.entity.JobStatus
import org.edu_sharing.rendering.entity.RenderingJob
import org.edu_sharing.rendering.exception.EntryNotFoundException
import org.edu_sharing.rendering.logic.ConversionRetrieval
import org.edu_sharing.rendering.repository.mongo.RenderingJobRepository
import org.edu_sharing.rendering.repository.mongo.SubJobRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service

@Service
class JobInfoService (
    private val jobRepository: RenderingJobRepository,
    private val subJobRepository: SubJobRepository,
    private val mapper: Mapper,
    private val conversionRetrieval: ConversionRetrieval,
    private val storageImplementation: StorageService,
    ){
    fun getJobInfo(jobId: String): JobInfoReply {
        val job = jobRepository.findByIdOrNull(ObjectId(jobId)) ?: throw EntryNotFoundException("Invalid jobId: $jobId")
        val module = if (job.mimeType.substringBefore("/") == "video") RenderModules.VIDEO else RenderModules.IMAGE
        if (isMainJobQueuedOrCopying(job)) {
            return JobInfoReply(mutableListOf(JobProgressInfo(status = job.status)), status = job.status, module = module)
        }
        val infoList: MutableList<JobProgressInfo> = mutableListOf()
        job.subJobs.forEach {
            val jobInfo = JobProgressInfo(quality = it.quality, status = it.status)
            when (it.status) {
                JobStatus.QUEUED -> {
                    val position = subJobRepository.countByIdBeforeAndStatusAndRoutingKey(it.id, it.status, it.routingKey)
                    jobInfo.progress = position
                }
                JobStatus.PROCESSING -> { jobInfo.progress = it.progress.toLong() }
                JobStatus.FINISHED -> {
                    // what do I do if there is nothing cached? For whatever reason
                    var cacheObject = mapper.renderingJobToCacheObject(job)
                    // This needs to be more abstract
                    cacheObject.quality = it.quality
                    cacheObject = conversionRetrieval.getCacheObjectWithConvertedMimeType(cacheObject)
                    jobInfo.objectLink = storageImplementation.getObjectLink(cacheObject)
                }
                JobStatus.FAILED -> {}
            }
            infoList.add(jobInfo)
        }
        return JobInfoReply(infoList, status = job.status, module = module)
    }

    private fun isMainJobQueuedOrCopying(job: RenderingJob): Boolean {
        val isQueued = job.status == JobStatus.QUEUED
        val isCopying = job.status == JobStatus.PROCESSING && job.subJobs.size == 0
        return isQueued || isCopying
    }
}