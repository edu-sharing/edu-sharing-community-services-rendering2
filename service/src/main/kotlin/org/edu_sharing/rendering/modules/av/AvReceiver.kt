package org.edu_sharing.rendering.modules.av

import org.apache.commons.lang3.NotImplementedException
import org.edu_sharing.rendering.core.ErrorStrings.GENERIC_CONVERSION_ERROR
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.core.exception.ConversionException
import org.edu_sharing.rendering.modules.av.audio.AudioConversionService
import org.edu_sharing.rendering.modules.av.audio.AudioRenderModule
import org.edu_sharing.rendering.modules.av.video.VideoConversionService
import org.edu_sharing.rendering.modules.av.video.VideoRenderModule
import org.edu_sharing.rendering.renderingJob.MainJobLogic
import org.edu_sharing.rendering.renderingJob.entity.SubJobStatus
import org.edu_sharing.rendering.renderingJob.queue.SubJobMessage
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.edu_sharing.rendering.storage.StorageService
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.*
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import java.time.Instant

@ConditionalOnAvConverter
@Component
class AvReceiver(
    private val mainJobLogic: MainJobLogic,
    private val subJobRepository: SubJobRepository,
    private val audioConversionService: AudioConversionService,
    private val videoConversionService: VideoConversionService,
    private val mapper: Mapper,
    private val storageImplementation: StorageService,
    private val audioModule: AudioRenderModule,
    private val videoModule: VideoRenderModule
) {

    companion object {
        const val MODULE_NOT_SUPPORTED_ERROR = "Module not supported for AV conversion:"
    }

    private val log = LoggerFactory.getLogger(javaClass)

    @RabbitListener(
        bindings = [
            QueueBinding(
                value = Queue(
                    name = "#{avQueueProperties.name}",
                    durable = "true",
                    arguments = [Argument(
                        name = "x-max-priority",
                        value = "#{videoConverterConfig.getMaxPriority()}",
                        type = "java.lang.Integer"
                    )]
                ),
                exchange = Exchange(name = "#{queueProperties.topicExchange}", type = "topic"),
                key = ["#{avQueueProperties.key}"]
            )
        ],
        containerFactory = "queueListenerContainerFactory",
        concurrency = "#{avQueueProperties.effectiveConcurrency}"
    )
    fun receiveMessage(message: SubJobMessage) {
        log.debug("Received AV sub-job message: id=${message.id}, quality=${message.quality}")
        val jobEntry = mainJobLogic.getMainJobEntry(message.id)
        if (jobEntry == null) {
            log.warn("Expected main job not found: " + message.id)
            return
        }
        var subJob = jobEntry.subJobs.firstOrNull { it.quality == message.quality }
        if (subJob == null) {
            log.error("Expected sub job not found for message: {}", message)
            mainJobLogic.processMainJob(message.id)
            return
        }
        val cacheObject = mapper.renderingJobToCacheObject(jobEntry)
        log.debug("Dispatching AV conversion: nodeId=${cacheObject.nodeId}, module=${jobEntry.module}, quality=${subJob.quality}")
        subJob.status = SubJobStatus.PROCESSING
        subJob.processingStartedDate = Instant.now()
        subJob = subJobRepository.save(subJob)
        try {
            val service = when(jobEntry.module) {
                audioModule.module() -> audioConversionService
                videoModule.module() -> videoConversionService
                else -> throw NotImplementedException("$MODULE_NOT_SUPPORTED_ERROR ${jobEntry.module}")
            }
            service.convert(
                cacheObject = cacheObject.deepCopy(),
                subJob = subJob
            )
            val finishedSubJob = subJobRepository.findByIdOrNull(subJob.id)
            if (finishedSubJob !== null) {
                finishedSubJob.status = SubJobStatus.FINISHED
                finishedSubJob.finishedDate = Instant.now()
                finishedSubJob.progress = 100
                subJobRepository.save(finishedSubJob)
                log.debug("AV sub-job FINISHED: nodeId=${cacheObject.nodeId}, quality=${subJob.quality}")
            }
        } catch (exception: Exception) {
            val failedSubJob = subJobRepository.findByIdOrNull(subJob.id)
            if (exception is ConversionException) {
                log.warn(exception.message, exception)
            } else {
                log.error(exception.message, exception)
            }
            if (failedSubJob != null) {
                failedSubJob.status = SubJobStatus.FAILED
                failedSubJob.finishedDate = Instant.now()
                failedSubJob.errorMessage = GENERIC_CONVERSION_ERROR
                subJobRepository.save(failedSubJob)
                log.debug("AV sub-job FAILED: nodeId=${cacheObject.nodeId}, quality=${subJob.quality}")
            }
        }
        if (mainJobLogic.processMainJob(message.id)) {
            storageImplementation.removeTempObject(cacheObject)
        }
    }
}
