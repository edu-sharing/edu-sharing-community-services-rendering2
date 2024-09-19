package org.edu_sharing.rendering.renderingJob.queue

import org.bson.types.ObjectId
import org.edu_sharing.rendering.storage.StorageService
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.edusharingRepo.ContentTransferService
import org.edu_sharing.rendering.modules.RenderModules
import org.edu_sharing.rendering.renderingJob.ConditionalOnJobManager
import org.edu_sharing.rendering.renderingJob.entity.JobStatus
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.edu_sharing.rendering.renderingJob.repository.RenderingJobRepository
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.slf4j.LoggerFactory
import org.springframework.amqp.core.AmqpTemplate
import org.springframework.amqp.rabbit.annotation.Exchange
import org.springframework.amqp.rabbit.annotation.Queue
import org.springframework.amqp.rabbit.annotation.QueueBinding
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component


@ConditionalOnJobManager
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

    @Value("\${app.queue.document.key}")
    lateinit var documentRoutingKey: String

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
            if (! jobEntry.conversionType) {
                storageImplementation.putObject(cacheObject, contentTransferService.getAsInputStream(cacheObject))
                jobEntry.status = JobStatus.FINISHED
                jobRepository.save(jobEntry)
                return
            } else {
                storageImplementation.putTempFile(cacheObject, contentTransferService.getAsInputStream(cacheObject))
            }
        } catch (_: Exception) {
            jobEntry.status = JobStatus.FAILED
            jobRepository.save(jobEntry)
            return
        }
        when (jobEntry.module) {
            RenderModules.IMAGE -> {
                this.createImageJob(jobEntry, message)
            }
            RenderModules.VIDEO, RenderModules.AUDIO -> {
                createAvJobs(jobEntry, message)
            }
            RenderModules.DOCUMENT, RenderModules.SPREADSHEET -> {
                createDocJob(jobEntry)
            }
            else -> {
                jobEntry.status = JobStatus.FAILED
                jobRepository.save(jobEntry)
                logger.warn("No implementation for render module")
            }
        }
    }

    private fun createImageJob(jobEntry: RenderingJob, message: RenderingJobMessage) {
        message.missingQualities.forEach {
            val imageJob = SubJob(routingKey = imageRoutingKey, quality = it, parent = jobEntry)
            jobEntry.subJobs.add(imageJob)
            subJobRepository.save(imageJob)
        }
        amqpTemplate.convertAndSend(topicExchangeName, imageRoutingKey, SubJobMessage(jobEntry.id.toString()))
    }

    private fun createAvJobs(jobEntry: RenderingJob, message: RenderingJobMessage) {
        message.missingQualities.forEach {
            val avJob = SubJob(routingKey = avRoutingKey, quality = it, parent = jobEntry)
            jobEntry.subJobs.add(avJob)
            subJobRepository.save(avJob)
            amqpTemplate.convertAndSend(topicExchangeName, avRoutingKey, SubJobMessage(jobEntry.id.toString(), it))
        }
    }

    private fun createDocJob(jobEntry: RenderingJob) {
        val documentJob = SubJob(routingKey = documentRoutingKey, parent = jobEntry)
        subJobRepository.save(documentJob)
        jobEntry.subJobs.add(documentJob)
        amqpTemplate.convertAndSend(topicExchangeName, documentRoutingKey, SubJobMessage(jobEntry.id.toString()))
    }
}
