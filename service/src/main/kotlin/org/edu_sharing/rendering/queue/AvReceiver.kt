package org.edu_sharing.rendering.queue

import org.edu_sharing.rendering.blobStorage.StorageService
import org.edu_sharing.rendering.dto.mapper.Mapper
import org.edu_sharing.rendering.dto.queue.SubJobMessage
import org.edu_sharing.rendering.entity.JobStatus
import org.edu_sharing.rendering.repository.mongo.SubJobRepository
import org.edu_sharing.rendering.service.AudioVideoConversionService
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.Exchange
import org.springframework.amqp.rabbit.annotation.Queue
import org.springframework.amqp.rabbit.annotation.QueueBinding
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

@Component
class AvReceiver(
    private val mainJobLogic: MainJobLogic,
    private val subJobRepository: SubJobRepository,
    private val conversionService: AudioVideoConversionService,
    private val mapper: Mapper,
    private val storageImplementation: StorageService
    ) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @RabbitListener(
        bindings = [
            QueueBinding(
                value = Queue(name = "\${app.queue.av.name}", durable = "false"),
                exchange = Exchange(name = "\${app.queue.topicExchange}", type = "topic"),
                key = ["\${app.queue.av.key}"]
            )
        ], containerFactory = "singlePrefetchConnectionFactory"
    )
    fun receiveMessage(message: SubJobMessage) {
        val jobEntry = mainJobLogic.getMainJobEntry(message.id)
        if (jobEntry == null) {
            logger.warn("Expected main job not found: " + message.id)
            return
        }
        val subJob = jobEntry.subJobs.first { it.quality == message.quality }
        val cacheObject = mapper.renderingJobToCacheObject(jobEntry)
        val originalMimeType = cacheObject.mimeType
        subJob.status = JobStatus.PROCESSING
        subJobRepository.save(subJob)
        var success = true
        try {
            conversionService.convert(cacheObject, subJob)
        } catch (exception: Exception) {
            logger.warn(exception.message)
            subJob.message = exception.message
            success = false
        } finally {
            subJob.status = if (success) JobStatus.FINISHED else JobStatus.FAILED
            subJobRepository.save(subJob)
        }
        if (mainJobLogic.processMainJob(message.id)) {
            cacheObject.mimeType = originalMimeType
            storageImplementation.removeObject(cacheObject, true)
        }
    }
}