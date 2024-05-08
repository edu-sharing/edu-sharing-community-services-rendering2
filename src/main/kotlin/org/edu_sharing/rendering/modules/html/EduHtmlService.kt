package org.edu_sharing.rendering.modules.html

import io.minio.errors.ErrorResponseException
import org.edu_sharing.rendering.blobStorage.StorageService
import org.edu_sharing.rendering.dto.ObjectLink
import org.edu_sharing.rendering.dto.RenderDataRequest
import org.edu_sharing.rendering.dto.RenderModules
import org.edu_sharing.rendering.dto.mapper.Mapper
import org.edu_sharing.rendering.dto.queue.RenderingJobMessage
import org.edu_sharing.rendering.entity.JobStatus
import org.edu_sharing.rendering.repository.mongo.RenderingJobRepository
import org.springframework.amqp.core.AmqpTemplate
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class EduHtmlService(
    private val mapper: Mapper,
    private val jobRepository: RenderingJobRepository,
    private val amqpTemplate: AmqpTemplate,
    private val renderingJobRepository: RenderingJobRepository,
    private val storageImplementation: StorageService
) {
    @Value("\${edu_sharing.queue.topicExchange}")
    lateinit var topicExchangeName: String

    @Value("\${edu_sharing.queue.edu_html.key}")
    lateinit var jobRoutingKey: String

    fun createJob(request: RenderDataRequest, module: RenderModules): String? {
        val existingJobs = renderingJobRepository.findAllByEsObjectId(request.nodeId)
            .filter { it.status == JobStatus.QUEUED || it.status == JobStatus.PROCESSING }
        if (existingJobs.isNotEmpty()) {
            return existingJobs[0].id.toString()
        }
        val job = mapper.cacheObjectToRenderingJob(mapper.renderDataRequestToCacheObject(request), module)
        job.module = RenderModules.EDUHTML
        jobRepository.save(job)
        val message = RenderingJobMessage(id = job.id.toString())
        amqpTemplate.convertAndSend(topicExchangeName, jobRoutingKey, message)
        return job.id.toString()
    }

    fun getObjectLink(nodeId: String): ObjectLink? {
        val bucket = "eduhtml"
        val indexPath = "$nodeId/index.html"
        try {
            storageImplementation.getFileProperties(bucket, indexPath)
        } catch (_: ErrorResponseException) {
            return null
        }
        return storageImplementation.getObjectLink(indexPath)
    }
}