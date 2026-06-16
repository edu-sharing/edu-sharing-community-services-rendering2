package org.edu_sharing.rendering.modules.eduhtml

import org.edu_sharing.generated.repository.backend.services.rest.client.model.Node
import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.core.dto.ObjectLink
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.core.exception.ResourceNotFoundException
import org.edu_sharing.rendering.renderingJob.entity.RenderingJobStatus
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.edu_sharing.rendering.renderingJob.queue.RenderingJobMessage
import org.edu_sharing.rendering.renderingJob.repository.RenderingJobRepository
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.edu_sharing.rendering.storage.StaticStorageService
import org.slf4j.LoggerFactory
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
    private val log = LoggerFactory.getLogger(javaClass)

    lateinit var topicExchangeName: String

    @Value("\${app.queue.eduHtml.key}")
    lateinit var jobRoutingKey: String

    fun createJob(node: Node, module: String): String {
        log.debug("Creating EduHTML job for nodeId ${node.ref.id}, module $module")
        val existingJobs = jobRepository.findAllByEsObjectId(node.ref.id)
            .filter { it.status <= RenderingJobStatus.PROCESSING }

        if (existingJobs.isNotEmpty()) {
            log.debug("Reusing existing EduHTML job ${existingJobs[0].id} for nodeId ${node.ref.id}")
            return existingJobs[0].id.toString()
        }

        var job = mapper.nodeToRenderingJob(
            node = node,
            module = module,
            isConversionType = true
        )
        job = jobRepository.save(job)

        val subJob = SubJob(
            routingKey = jobRoutingKey,
            parent = job
        )
        subJobRepository.save(subJob)

        val message = RenderingJobMessage(id = job.id.toString())
        log.debug("Sending EduHTML job message for jobId ${job.id} to queue $jobRoutingKey")
        amqpTemplate.convertAndSend(topicExchangeName, jobRoutingKey, message)

        return job.id.toString()
    }

    fun getObjectLink(cacheObject: CacheObject): ObjectLink {
        log.debug("Looking up cached EduHTML index.html for nodeId ${cacheObject.nodeId}")
        val fileExists = storageImplementation.objectExists(cacheObject, "index.html")
        if (!fileExists) {
            throw ResourceNotFoundException("Resource ${cacheObject.nodeId} not cached")
        }

        return storageImplementation.getObjectLink(cacheObject, "index.html")
    }
}
