package org.edu_sharing.rendering.queue

import org.bson.types.ObjectId
import org.edu_sharing.rendering.blobStorage.StorageService
import org.edu_sharing.rendering.dto.mapper.Mapper
import org.edu_sharing.rendering.dto.queue.RenderingJobMessage
import org.edu_sharing.rendering.dto.queue.SubJobMessage
import org.edu_sharing.rendering.entity.JobStatus
import org.edu_sharing.rendering.entity.RenderingJob
import org.edu_sharing.rendering.entity.SubJob
import org.edu_sharing.rendering.repository.mongo.RenderingJobRepository
import org.edu_sharing.rendering.repository.mongo.SubJobRepository
import org.edu_sharing.rendering.service.ContentTransferService
import org.slf4j.LoggerFactory
import org.springframework.amqp.core.AmqpTemplate
import org.springframework.amqp.rabbit.annotation.Exchange
import org.springframework.amqp.rabbit.annotation.Queue
import org.springframework.amqp.rabbit.annotation.QueueBinding
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component


@Component
class JobReceiver(
    private val jobRepository: RenderingJobRepository,
    private val subJobRepository: SubJobRepository,
    private val storageImplementation: StorageService,
    private val amqpTemplate: AmqpTemplate,
    private val mapper: Mapper,
    private val contentTransferService: ContentTransferService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Value("\${app.queue.image.key}")
    lateinit var imageRoutingKey: String

    @Value("\${app.queue.av.key}")
    lateinit var avRoutingKey: String

    @Value("\${app.queue.topicExchange}")
    lateinit var topicExchangeName: String

    @RabbitListener(
        bindings = [
            QueueBinding(
                value = Queue(name = "\${app.queue.job.name}", durable = "false"),
                exchange = Exchange(name = "\${app.queue.topicExchange}", type = "topic"),
                key = ["\${app.queue.job.key}"]
            )
        ]
    )
    fun receiveMessage(message: RenderingJobMessage) {
        val jobEntry = jobRepository.findByIdOrNull(ObjectId(message.id)) ?: return
        jobEntry.status = JobStatus.PROCESSING
        jobRepository.save(jobEntry)
        val cacheObject = mapper.renderingJobToCacheObject(jobEntry)
        try {
            this.storageImplementation.putTempFile(cacheObject, contentTransferService.getAsInputStream(cacheObject))
        } catch (exception: Exception) {
            jobEntry.status = JobStatus.FAILED
            jobRepository.save(jobEntry)
            return
        }
        when (cacheObject.type) {
            "file-image" -> {
                this.createImageJob(jobEntry, message)
            }
            "file-video", "file-audio" -> {
                createAvJobs(jobEntry, message)
            }
            else -> {
                jobEntry.status = JobStatus.FAILED
                jobRepository.save(jobEntry)
                logger.warn("No implementation for type " + cacheObject.type)
            }
        }
    }

    private fun createImageJob(jobEntry: RenderingJob, message: RenderingJobMessage) {
        message.missingQualities.forEach {
            val imageJob = SubJob(routingKey = imageRoutingKey, quality = it, parent = jobEntry)
            subJobRepository.save(imageJob)
        }
        amqpTemplate.convertAndSend(topicExchangeName, imageRoutingKey, SubJobMessage(jobEntry.id.toString()))
    }

    private fun createAvJobs(jobEntry: RenderingJob, message: RenderingJobMessage) {
        message.missingQualities.forEach {
            val avJob = SubJob(routingKey = avRoutingKey, quality = it, parent = jobEntry)
            subJobRepository.save(avJob)
            amqpTemplate.convertAndSend(topicExchangeName, avRoutingKey, SubJobMessage(jobEntry.id.toString(), it))
        }
    }
}
