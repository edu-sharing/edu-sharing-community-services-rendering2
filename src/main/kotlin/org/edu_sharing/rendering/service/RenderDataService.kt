package org.edu_sharing.rendering.service

import io.minio.errors.ErrorResponseException
import org.edu_sharing.rendering.blobStorage.StorageService
import org.edu_sharing.rendering.dto.CacheObject
import org.edu_sharing.rendering.dto.RenderDataRequest
import org.edu_sharing.rendering.dto.RenderDataResponse
import org.edu_sharing.rendering.dto.mapper.Mapper
import org.edu_sharing.rendering.dto.queue.RenderingJobMessage
import org.edu_sharing.rendering.entity.JobStatus
import org.edu_sharing.rendering.logic.ConversionRetrieval
import org.edu_sharing.rendering.repository.mongo.RenderingJobRepository
import org.springframework.amqp.core.AmqpTemplate
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ResourceLoader
import org.springframework.stereotype.Service

@Service
class RenderDataService (
    private val storageImplementation: StorageService,
    @Qualifier("webApplicationContext")
    private val resourceLoader: ResourceLoader,
    private val mongoRepo: RenderingJobRepository,
    private val amqpTemplate: AmqpTemplate,
    private val mapper: Mapper,
    private val conversionRetrieval: ConversionRetrieval
    ) {

    @Value("\${edu_sharing.topicExchangeName}")
    lateinit var topicExchangeName: String

    @Value("\${edu_sharing.jobRoutingKey}")
    lateinit var jobRoutingKey: String

    fun getRenderData(request: RenderDataRequest): RenderDataResponse {
        val (objectLinkList, jobId) = this.compileResponseLists(mapper.renderDataRequestToCacheObject(request))
        return RenderDataResponse(objectLinkList, jobId)
    }

    fun compileResponseLists(cacheObject: CacheObject): Pair<MutableList<String>, String?> {
        val objectLinkList = mutableListOf<String>()
        var jobId: String? = null
        if (conversionRetrieval.checkIsConversionObject(cacheObject)) {
            val missingResolutions = mutableListOf<Int>()
            this.conversionRetrieval.getMimeTypeSpecificQualities(cacheObject.mimeType).forEach {
                cacheObject.quality = it
                val link = this.retrieveObjectLink(cacheObject)
                if (link != null) {
                    objectLinkList.add(link)
                } else {
                    missingResolutions.add(it)
                }
            }
            if (missingResolutions.size > 0) {
                jobId = this.createJob(cacheObject, missingResolutions)
            }
        } else {
            val link = retrieveObjectLink(cacheObject)
            if (link != null) {
                objectLinkList.add(link)
            } else {
                this.cacheObjectData(cacheObject)
            }
        }
        return objectLinkList to jobId
    }

    private fun retrieveObjectLink(cacheObject: CacheObject): String? {
        val lookUpObject = conversionRetrieval.getCacheObjectWithConvertedMimeType(cacheObject)
        if (! storageImplementation.isObjectExisting(lookUpObject)) {
            return null
        }
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
        } catch (_: NoSuchElementException) {}
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
        val file = resourceLoader.getResource("classpath:lviv.jpg").file
        cacheObject.size = file.length()
        this.storageImplementation.putObject(cacheObject, file.inputStream())
    }
}