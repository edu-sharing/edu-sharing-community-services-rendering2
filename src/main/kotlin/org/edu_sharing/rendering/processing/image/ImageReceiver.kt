package org.edu_sharing.rendering.processing.image

import org.edu_sharing.rendering.config.annotation.ConditionalOnConverter
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
class ImageReceiver(
    private val mainJobLogic: MainJobLogic,
    private val subJobRepository: SubJobRepository,
    private val conversionService: ImageConversionService,
    private val mapper: Mapper
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @RabbitListener(
        bindings = [
            QueueBinding(
                value = Queue(name = "\${edu_sharing.queue.image.name}", durable = "false"),
                exchange = Exchange(name = "\${edu_sharing.queue.topicExchange}", type = "topic"),
                key = ["\${edu_sharing.queue.image.key}"]
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
        val cacheObject = mapper.renderingJobToCacheObject(jobEntry)
        val sourceImage = conversionService.fetchSourceImage(cacheObject)
        jobEntry.subJobs.forEach {
            try {
                it.status = JobStatus.PROCESSING
                subJobRepository.save(it)
                this.conversionService.convert(cacheObject, it.quality, sourceImage)
                it.status = JobStatus.FINISHED
            } catch (exception: Exception) {
                logger.warn(exception.message)
                it.status = JobStatus.FAILED
            }
            subJobRepository.save(it)
        }
        mainJobLogic.processMainJob(message.id)
    }
}