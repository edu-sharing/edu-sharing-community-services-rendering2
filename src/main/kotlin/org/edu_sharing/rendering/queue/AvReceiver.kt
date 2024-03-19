package org.edu_sharing.rendering.queue

import org.bson.types.ObjectId
import org.edu_sharing.rendering.dto.mapper.Mapper
import org.edu_sharing.rendering.dto.queue.SubJobMessage
import org.edu_sharing.rendering.entity.JobStatus
import org.edu_sharing.rendering.repository.mongo.RenderingJobRepository
import org.edu_sharing.rendering.repository.mongo.SubJobRepository
import org.edu_sharing.rendering.service.AudioVideoConversionService
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.Exchange
import org.springframework.amqp.rabbit.annotation.Queue
import org.springframework.amqp.rabbit.annotation.QueueBinding
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class AvReceiver(
    private val jobRepository: RenderingJobRepository,
    private val subJobRepository: SubJobRepository,
    private val conversionService: AudioVideoConversionService,
    private val mapper: Mapper
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @RabbitListener(
        bindings = [
            QueueBinding(
                value = Queue(name = "\${edu_sharing.queue.av.name}", durable = "false"),
                exchange = Exchange(name = "\${edu_sharing.queue.topicExchange}", type = "topic"),
                key = ["\${edu_sharing.queue.av.key}"]
            )
        ], containerFactory = "singlePrefetchConnectionFactory"
    )
    fun receiveMessage(message: SubJobMessage) {
        val jobEntry = jobRepository.findByIdOrNull(ObjectId(message.id))
        if (jobEntry == null) {
            logger.warn("Expected main job not found: " + message.id)
            return
        }
        val subJob = jobEntry.subJobs.first { it.quality == message.quality }
        val cacheObject = mapper.renderingJobToCacheObject(jobEntry)
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
        processMainJob(ObjectId(message.id))
    }

    private fun processMainJob(mongoId: ObjectId) {
        val jobEntry = jobRepository.findByIdOrNull(mongoId)
        if (jobEntry != null) {
            val areSomeProcessingOrQueued = jobEntry.subJobs.firstOrNull { it.status < JobStatus.FINISHED } != null
            if (areSomeProcessingOrQueued) {
                return
            }
            val areAllFinished = jobEntry.subJobs.firstOrNull { it.status == JobStatus.FAILED } == null
            jobEntry.status = if (areAllFinished) JobStatus.FINISHED else JobStatus.FAILED
            jobRepository.save(jobEntry)
        }
    }
}