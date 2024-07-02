package org.edu_sharing.rendering.processing.av

import org.apache.commons.lang3.NotImplementedException
import org.edu_sharing.rendering.blobStorage.StorageService
import org.edu_sharing.rendering.config.annotation.ConditionalOnConverter
import org.edu_sharing.rendering.dto.RenderModules
import org.edu_sharing.rendering.dto.mapper.Mapper
import org.edu_sharing.rendering.dto.queue.SubJobMessage
import org.edu_sharing.rendering.entity.JobStatus
import org.edu_sharing.rendering.processing.MainJobLogic
import org.edu_sharing.rendering.repository.mongo.SubJobRepository
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.Exchange
import org.springframework.amqp.rabbit.annotation.Queue
import org.springframework.amqp.rabbit.annotation.QueueBinding
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

@ConditionalOnConverter
@Component
class AvReceiver(
    private val mainJobLogic: MainJobLogic,
    private val subJobRepository: SubJobRepository,
    private val audioConversionService: AudioConversionService,
    private val videoConversionService: VideoConversionService,
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
        val subJob = jobEntry.subJobs.firstOrNull { it.quality == message.quality }
        if (subJob == null) {
            logger.error("Expected sub job not found for message: {}", message)
            mainJobLogic.processMainJob(message.id)
            return
        }
        val cacheObject = mapper.renderingJobToCacheObject(jobEntry)
        subJob.status = JobStatus.PROCESSING
        subJobRepository.save(subJob)
        val service = when(jobEntry.module) {
            RenderModules.AUDIO -> audioConversionService
            RenderModules.VIDEO -> videoConversionService
            else -> throw NotImplementedException(jobEntry.module.toString())
        }
        var success = true
        try {
            service.convert(
                cacheObject = cacheObject.deepCopy(),
                subJob = subJob
            )
        } catch (exception: Exception) {
            logger.error(exception.message)
            subJob.message = exception.message
            success = false
        } finally {
            subJob.status = if (success) JobStatus.FINISHED else JobStatus.FAILED
            subJobRepository.save(subJob)
        }
        if (mainJobLogic.processMainJob(message.id)) {
            storageImplementation.removeObject(cacheObject, true)
        }
    }
}