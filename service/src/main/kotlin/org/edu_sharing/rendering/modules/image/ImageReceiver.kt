package org.edu_sharing.rendering.modules.image

import org.edu_sharing.rendering.core.ErrorStrings
import org.edu_sharing.rendering.core.annotation.ConditionalOnConverter
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.core.exception.ConversionException
import org.edu_sharing.rendering.renderingJob.MainJobLogic
import org.edu_sharing.rendering.renderingJob.entity.SubJobStatus
import org.edu_sharing.rendering.renderingJob.queue.SubJobMessage
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.Exchange
import org.springframework.amqp.rabbit.annotation.Queue
import org.springframework.amqp.rabbit.annotation.QueueBinding
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component
import java.time.Instant

@ConditionalOnConverter
@Component
class ImageReceiver(
    private val mainJobLogic: MainJobLogic,
    private val subJobRepository: SubJobRepository,
    private val conversionService: ImageConversionService,
    private val mapper: Mapper
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @RabbitListener(
        bindings = [
            QueueBinding(
                value = Queue(name = "#{imageQueueProperties.name}", durable = "true"),
                exchange = Exchange(name = "#{queueProperties.topicExchange}", type = "topic"),
                key = ["#{imageQueueProperties.key}"]
            )
        ],
        containerFactory = "queueListenerContainerFactory",
        concurrency = "#{imageQueueProperties.effectiveConcurrency}"
    )
    fun receiveMessage(message: SubJobMessage) {
        log.debug("Received image sub-job message: id=${message.id}")
        val jobEntry = mainJobLogic.getMainJobEntry(message.id)
        if (jobEntry == null) {
            log.warn("Expected main job not found: " + message.id)
            return
        }
        // RabbitMQ is at-least-once: on a redelivery, sub-jobs that already reached a terminal state are not
        // converted again. A sub-job left PROCESSING by the dead delivery is deliberately restarted.
        val pendingSubJobs = jobEntry.subJobs.filter { it.status < SubJobStatus.FINISHED }
        if (pendingSubJobs.isEmpty()) {
            log.debug("Image job ${message.id} has only terminal sub-jobs; dropping redelivered message")
            // Idempotent: resolves a main job whose previous delivery died before aggregating.
            mainJobLogic.processMainJob(message.id)
            return
        }
        val cacheObject = mapper.renderingJobToCacheObject(jobEntry)
        log.debug("Processing image job for nodeId=${cacheObject.nodeId}, pendingSubJobCount=${pendingSubJobs.size}")
        val maxQuality = pendingSubJobs.maxOfOrNull { it.quality } ?: 0
        val sourceImage = try {
            conversionService.fetchSourceImage(cacheObject, maxQuality)
        } catch (exception: Exception) {
            if (exception is ConversionException) {
                log.warn(exception.message, exception)
            } else {
                log.error(exception.message, exception)
            }
            pendingSubJobs.forEach {
                it.status = SubJobStatus.FAILED
                it.finishedDate = Instant.now()
                it.errorMessage = ErrorStrings.GENERIC_CONVERSION_ERROR
                subJobRepository.save(it)
            }
            conversionService.deleteTempFile(cacheObject)
            mainJobLogic.processMainJob(message.id)
            return
        }
        pendingSubJobs.forEach {
            var subJob = it
            log.debug("Converting image sub-job: quality=${subJob.quality} for nodeId=${cacheObject.nodeId}")
            try {
                subJob.status = SubJobStatus.PROCESSING
                subJob.processingStartedDate = Instant.now()
                subJob = subJobRepository.save(subJob)
                conversionService.convert(cacheObject, subJob.quality, sourceImage)
                subJob.status = SubJobStatus.FINISHED
                subJob.finishedDate = Instant.now()
                log.debug("Image sub-job FINISHED: quality=${subJob.quality} for nodeId=${cacheObject.nodeId}")
            } catch (exception: Exception) {
                log.warn(exception.message, exception)
                subJob.status = SubJobStatus.FAILED
                subJob.finishedDate = Instant.now()
            }
            subJobRepository.save(subJob)
        }
        conversionService.deleteTempFile(cacheObject)
        mainJobLogic.processMainJob(message.id)
    }
}
