package org.edu_sharing.rendering.modules.eduhtml

import org.edu_sharing.rendering.blobStorage.StorageService
import org.edu_sharing.rendering.dto.CacheObject
import org.edu_sharing.rendering.dto.ObjectLink
import org.edu_sharing.rendering.dto.RenderDataRequest
import org.edu_sharing.rendering.dto.RenderModules
import org.edu_sharing.rendering.dto.mapper.Mapper
import org.edu_sharing.rendering.dto.queue.RenderingJobMessage
import org.edu_sharing.rendering.entity.JobStatus
import org.edu_sharing.rendering.entity.SubJob
import org.edu_sharing.rendering.repository.mongo.RenderingJobRepository
import org.edu_sharing.rendering.repository.mongo.SubJobRepository
import org.springframework.amqp.core.AmqpTemplate
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class EduHtmlService(
    private val mapper: Mapper,
    private val jobRepository: RenderingJobRepository,
    private val amqpTemplate: AmqpTemplate,
    private val storageImplementation: StorageService,
    private val subJobRepository: SubJobRepository
) {
    @Value("\${app.queue.topicExchange}")
    lateinit var topicExchangeName: String

    @Value("\${app.queue.eduHtml.key}")
    lateinit var jobRoutingKey: String

    fun createJob(request: RenderDataRequest, module: RenderModules): String {
        val existingJobs = jobRepository.findAllByEsObjectId(request.nodeId)
            .filter { it.status <= JobStatus.PROCESSING }

        if (existingJobs.isNotEmpty()) {
            return existingJobs[0].id.toString()
        }

        val job = mapper.renderDataRequestToRenderingJob(request, module)
        jobRepository.save(job)

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
        //TODO check if file exists in storage
//        storageImplementation.getFileProperties(bucket, indexPath)
        return storageImplementation.getObjectLink(cacheObject, "index.html")
    }
}
