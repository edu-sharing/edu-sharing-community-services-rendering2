package org.edu_sharing.rendering.modules.image

import org.edu_sharing.rendering.core.annotation.ConditionalOnConverter
import org.edu_sharing.rendering.core.dto.mapper.Mapper
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
                value = Queue(name = "\${app.queue.image.name}", durable = "false"),
                exchange = Exchange(name = "\${app.queue.topicExchange}", type = "topic"),
                key = ["\${app.queue.image.key}"]
            )
        ],
        containerFactory = "singlePrefetchConnectionFactory"
    )
    fun receiveMessage(message: SubJobMessage) {
        val jobEntry = mainJobLogic.getMainJobEntry(message.id)
        if (jobEntry == null) {
            log.warn("Expected main job not found: " + message.id)
            return
        }
        val cacheObject = mapper.renderingJobToCacheObject(jobEntry)
        val sourceImage = conversionService.fetchSourceImage(cacheObject)
        jobEntry.subJobs.forEach {
            var subJob = it
            try {
                subJob.status = SubJobStatus.PROCESSING
                subJob = subJobRepository.save(subJob)
                conversionService.convert(cacheObject, subJob.quality, sourceImage)
                subJob.status = SubJobStatus.FINISHED
            } catch (exception: Exception) {
                log.warn(exception.message, exception)
                subJob.status = SubJobStatus.FAILED
            }
            subJobRepository.save(subJob)
        }
        conversionService.deleteTempFile(cacheObject)
        mainJobLogic.processMainJob(message.id)
    }
}
