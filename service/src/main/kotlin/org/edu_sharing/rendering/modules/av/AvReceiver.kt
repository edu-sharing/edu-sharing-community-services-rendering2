package org.edu_sharing.rendering.modules.av

import org.apache.commons.lang3.NotImplementedException
import org.edu_sharing.rendering.core.annotation.ConditionalOnConverter
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.modules.av.audio.AudioConversionService
import org.edu_sharing.rendering.modules.av.audio.AudioRenderModule
import org.edu_sharing.rendering.modules.av.video.VideoConversionService
import org.edu_sharing.rendering.modules.av.video.VideoRenderModule
import org.edu_sharing.rendering.renderingJob.MainJobLogic
import org.edu_sharing.rendering.renderingJob.entity.JobStatus
import org.edu_sharing.rendering.renderingJob.queue.SubJobMessage
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.edu_sharing.rendering.storage.StorageService
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.Argument
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
    private val storageImplementation: StorageService,
    private val audioModule: AudioRenderModule,
    private val videoModule: VideoRenderModule
) {

    companion object {
        const val MODULE_NOT_SUPPORTED_ERROR = "Module not supported for AV conversion:"
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    @RabbitListener(
        bindings = [
            QueueBinding(
                value = Queue(
                    name = "\${app.queue.av.name}",
                    durable = "false",
                    arguments = [Argument(
                        name = "x-max-priority",
                        value = "#{videoConverterConfig.getMaxPriority()}",
                        type = "java.lang.Integer"
                    )]
                ),
                exchange = Exchange(name = "\${app.queue.topicExchange}", type = "topic"),
                key = ["\${app.queue.av.key}"]
            )
        ],
        containerFactory = "singlePrefetchConnectionFactory"
    )
    fun receiveMessage(message: SubJobMessage) {
        val jobEntry = mainJobLogic.getMainJobEntry(message.id)
        if (jobEntry == null) {
            logger.warn("Expected main job not found: " + message.id)
            return
        }
        var subJob = jobEntry.subJobs.firstOrNull { it.quality == message.quality }
        if (subJob == null) {
            logger.error("Expected sub job not found for message: {}", message)
            mainJobLogic.processMainJob(message.id)
            return
        }
        val cacheObject = mapper.renderingJobToCacheObject(jobEntry)
        subJob.status = JobStatus.PROCESSING
        subJob = subJobRepository.save(subJob)
        var success = true
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
        } catch (exception: Exception) {
            logger.error(exception.message)
            subJob.message = exception.message
            success = false
        } finally {
            if (success) {
                subJob.status = JobStatus.FINISHED
                subJob.progress = 100
            } else {
                subJob.status = JobStatus.FAILED
            }
            subJobRepository.save(subJob)
        }
        if (mainJobLogic.processMainJob(message.id)) {
            storageImplementation.removeObject(cacheObject, true)
        }
    }
}
