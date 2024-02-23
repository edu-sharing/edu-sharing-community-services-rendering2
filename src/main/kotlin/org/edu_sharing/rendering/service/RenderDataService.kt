package org.edu_sharing.rendering.service

import io.minio.errors.ErrorResponseException
import org.edu_sharing.rendering.blobStorage.StorageService
import org.edu_sharing.rendering.dto.CacheObject
import org.edu_sharing.rendering.dto.RenderDataRequest
import org.edu_sharing.rendering.dto.RenderDataResponse
import org.edu_sharing.rendering.dto.queue.RenderingJobMessage
import org.edu_sharing.rendering.entity.JobStatus
import org.edu_sharing.rendering.entity.RenderingJob
import org.edu_sharing.rendering.repository.mongo.RenderingJobRepository
import org.springframework.amqp.core.AmqpTemplate
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ResourceLoader
import org.springframework.stereotype.Service

@Service
class RenderDataService (
    private val storageImplementation: StorageService,
    @Qualifier("webApplicationContext") private val resourceLoader: ResourceLoader,
    private val mongoRepo: RenderingJobRepository,
    private val amqpTemplate: AmqpTemplate
    ) {
    @Value("\${edu_sharing.image_sizes}")
    lateinit var imageSizes: List<Int>

    @Value("\${edu_sharing.topicExchangeName}")
    lateinit var topicExchangeName: String

    @Value("\${edu_sharing.jobRoutingKey}")
    lateinit var jobRoutingKey: String

    var objectLinkList = mutableListOf<String>()
    var jobId: String? = null

    fun getRenderData(request: RenderDataRequest): RenderDataResponse {
        this.objectLinkList = mutableListOf()
        val cacheObject = CacheObject(
            nodeId = request.nodeId,
            type = request.type,
            hash = request.hash,
            size = request.size,
            mimeType = request.mimeType
        )
        this.compileResponseLists(cacheObject)
        return RenderDataResponse(objectLinkList, jobId)
    }

    fun compileResponseLists(cacheObject: CacheObject) {
        if (cacheObject.type == "image") {
            val missingResolutions = mutableListOf<Int>()
            this.imageSizes.forEach {
                cacheObject.quality = it
                val link = this.retrieveObjectLink(cacheObject)
                if (link != null) {
                    this.objectLinkList.add(link)
                } else {
                    missingResolutions.add(it)
                }
            }
            if (missingResolutions.size > 0) {
                this.jobId = this.createJob(cacheObject, missingResolutions)
            }
        } else {
            val link = retrieveObjectLink(cacheObject)
            if (link != null) {
                this.objectLinkList.add(link)
            } else {
                this.cacheObjectData(cacheObject)
            }
        }
    }

    private fun retrieveObjectLink(cacheObject: CacheObject): String? {
        return try {
            storageImplementation.getObjectLink(cacheObject)
        } catch (_: ErrorResponseException) {
            null
        }
    }

    private fun createJob(cacheObject: CacheObject, qualities: MutableList<Int>): String {
        val existingJobs = mongoRepo.findAllByEsObjectId(cacheObject.nodeId)
        try {
            val unfinishedJob = existingJobs.first { it.status != JobStatus.FINISHED && it.status != JobStatus.FAILED }
            unfinishedJob.subJobs.forEach {
                qualities.remove(it.quality)
            }
            if (qualities.size == 0) {
                return unfinishedJob.id.toString()
            } else {
                throw Exception(unfinishedJob.id.toString() + ": Quality list changed amidst ongoing conversion")
            }
        } catch (_: NoSuchElementException) {}
        val renderingJob = RenderingJob(
            esObjectType = cacheObject.type,
            esObjectId = cacheObject.nodeId,
            esHash = cacheObject.hash,
            mimeType = cacheObject.mimeType,
            origin = "lviv.jpg"
        )
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