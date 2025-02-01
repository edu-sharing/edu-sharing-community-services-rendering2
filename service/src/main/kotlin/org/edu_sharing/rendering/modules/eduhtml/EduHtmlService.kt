package org.edu_sharing.rendering.modules.eduhtml

import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.core.dto.ObjectLink
import org.edu_sharing.rendering.core.dto.RenderDataRequest
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.core.exception.ResourceNotFoundException
import org.edu_sharing.rendering.renderingJob.entity.JobStatus
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.edu_sharing.rendering.renderingJob.queue.RenderingJobMessage
import org.edu_sharing.rendering.renderingJob.repository.RenderingJobRepository
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.edu_sharing.rendering.storage.StaticStorageService
import org.springframework.amqp.core.AmqpTemplate
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class EduHtmlService(
    private val mapper: Mapper,
    private val jobRepository: RenderingJobRepository,
    private val amqpTemplate: AmqpTemplate,
    private val storageImplementation: StaticStorageService,
    private val subJobRepository: SubJobRepository
) {
    @Value("\${app.queue.topicExchange}")
    lateinit var topicExchangeName: String

    @Value("\${app.queue.eduHtml.key}")
    lateinit var jobRoutingKey: String

    fun createJob(request: RenderDataRequest, module: String): String {
        val existingJobs = jobRepository.findAllByEsObjectId(request.nodeId)
            .filter { it.status <= JobStatus.PROCESSING }

        if (existingJobs.isNotEmpty()) {
            return existingJobs[0].id.toString()
        }

        var job = mapper.renderDataRequestToRenderingJob(
            request = request,
            module = module,
            conversionType = true
        )
        job = jobRepository.save(job)

        val subJob = SubJob(
            routingKey = jobRoutingKey,
            parent = job
        )
        subJobRepository.save(subJob)

        val message = RenderingJobMessage(id = job.id.toString())
        amqpTemplate.convertAndSend(topicExchangeName, jobRoutingKey, message)

        return job.id.toString()
    }

    fun getObjectLink(cacheObject: CacheObject): ObjectLink {
        val fileExists = storageImplementation.objectExists(cacheObject, "index.html")
        if (!fileExists) {
            throw ResourceNotFoundException("Resource ${cacheObject.nodeId} not cached")
        }

        return storageImplementation.getObjectLink(cacheObject, "index.html")
    }
}
