package org.edu_sharing.rendering.service

import io.minio.errors.ErrorResponseException
import org.edu_sharing.rendering.blobStorage.StorageService
import org.edu_sharing.rendering.dto.*
import org.edu_sharing.rendering.dto.mapper.Mapper
import org.edu_sharing.rendering.dto.queue.RenderingJobMessage
import org.edu_sharing.rendering.entity.JobStatus
import org.edu_sharing.rendering.logic.ConversionRetrieval
import org.edu_sharing.rendering.repository.mongo.RenderingJobRepository
import org.springframework.amqp.core.AmqpTemplate
import org.springframework.beans.factory.annotation.Value
import org.springframework.lang.Nullable
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service

@Service
class RenderDataService(
    private val storageImplementation: StorageService,
    private val mongoRepo: RenderingJobRepository,
    private val amqpTemplate: AmqpTemplate,
    private val mapper: Mapper,
    private val conversionRetrieval: ConversionRetrieval,
    private val contentTransferService: ContentTransferService,
    private val renderModuleMappingService: RenderModuleMappingService,
    @Nullable
    private val moodleService: MoodleService?
) {

    @Value("\${edu_sharing.queue.topicExchange}")
    lateinit var topicExchangeName: String

    @Value("\${edu_sharing.queue.job.key}")
    lateinit var jobRoutingKey: String

    @PreAuthorize("hasPermission(#request.nodeId, 'Read')")
    fun getRenderData(request: RenderDataRequest): RenderDataResponse {
        val response = RenderDataResponse(module = renderModuleMappingService.getModule(request))
        if ((response.module === RenderModules.MOODLE || response.module === RenderModules.SCORM) && moodleService !== null) {
            response.objectLinks = mutableListOf()
            response.jobId = moodleService.createJob(request)
            if (response.jobId != null) {
                return response
            }
        }
        val (objectLinkList, jobId) = this.compileResponseLists(mapper.renderDataRequestToCacheObject(request))
        response.objectLinks = objectLinkList
        response.jobId = jobId

        return response
    }

    fun compileResponseLists(cacheObject: CacheObject): Pair<MutableList<ObjectLink>, String?> {
        val objectLinkList = mutableListOf<ObjectLink>()
        var jobId: String? = null
        if (conversionRetrieval.checkIsConversionObject(cacheObject)) {
            var missingResolutions = mutableListOf<Int>()
            var highestResolution = Int.MAX_VALUE
            this.conversionRetrieval.getMimeTypeSpecificQualities(cacheObject.mimeType).forEach {
                cacheObject.quality = it
                val link = this.retrieveObjectLink(cacheObject)
                if (link != null) {
                    objectLinkList.add(link)
                    if (link.isHighestQuality) highestResolution = link.height
                } else {
                    missingResolutions.add(it)
                }
            }
            missingResolutions = missingResolutions.filter { it < highestResolution }.toMutableList()
            if (missingResolutions.size > 0) {
                jobId = this.createJob(cacheObject, missingResolutions)
            }
        } else {
            val link = retrieveObjectLink(cacheObject)
            if (link != null) {
                objectLinkList.add(link)
            } else {
                this.cacheObjectData(cacheObject)
                objectLinkList.add(retrieveObjectLink(cacheObject) ?: ObjectLink(link = ""))
            }
        }
        return objectLinkList to jobId
    }

    private fun retrieveObjectLink(cacheObject: CacheObject): ObjectLink? {
        val lookUpObject = conversionRetrieval.getCacheObjectWithConvertedMimeType(cacheObject)
        return try {
            storageImplementation.getObjectLink(lookUpObject)
        } catch (_: ErrorResponseException) {
            null
        }
    }

    private fun createJob(cacheObject: CacheObject, qualities: MutableList<Int>): String {
        val existingJobs = mongoRepo.findAllByEsObjectId(cacheObject.nodeId)
        try {
            val unfinishedJob = existingJobs.first { it.status != JobStatus.FINISHED && it.status != JobStatus.FAILED }
            unfinishedJob.subJobs.forEach { qualities.remove(it.quality) }
            if (qualities.size == 0) {
                return unfinishedJob.id.toString()
            } else {
                throw Exception(unfinishedJob.id.toString() + ": Quality list changed amidst ongoing conversion")
            }
        } catch (_: NoSuchElementException) {
        }
        val renderingJob = mapper.cacheObjectToRenderingJob(cacheObject)
        mongoRepo.save(renderingJob)
        val jobMessage = RenderingJobMessage(
            id = renderingJob.id.toString(),
            missingQualities = qualities.toList()
        )
        amqpTemplate.convertAndSend(this.topicExchangeName, this.jobRoutingKey, jobMessage)
        return renderingJob.id.toString()
    }

    private fun cacheObjectData(cacheObject: CacheObject) {
        val objectInputStream = contentTransferService.getAsInputStream(cacheObject)
        this.storageImplementation.putObject(cacheObject, objectInputStream)
    }
}