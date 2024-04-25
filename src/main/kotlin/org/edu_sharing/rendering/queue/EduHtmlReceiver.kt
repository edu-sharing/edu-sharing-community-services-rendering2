package org.edu_sharing.rendering.queue

import org.bson.types.ObjectId
import org.edu_sharing.rendering.dto.mapper.Mapper
import org.edu_sharing.rendering.dto.queue.RenderingJobMessage
import org.edu_sharing.rendering.entity.JobStatus
import org.edu_sharing.rendering.entity.SubJob
import org.edu_sharing.rendering.repository.mongo.RenderingJobRepository
import org.edu_sharing.rendering.repository.mongo.SubJobRepository
import org.edu_sharing.rendering.service.EduHtmlService
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.Exchange
import org.springframework.amqp.rabbit.annotation.Queue
import org.springframework.amqp.rabbit.annotation.QueueBinding
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class EduHtmlReceiver (
    private val eduHtmlService: EduHtmlService,
    private val renderingJobRepository: RenderingJobRepository,
    private val subJobRepository: SubJobRepository,
    private val mainJobLogic: MainJobLogic,
    private val mapper: Mapper
    ) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Value("\${edu_sharing.queue.edu_html.key}")
    lateinit var jobRoutingKey: String

    @RabbitListener(
        bindings = [
            QueueBinding(
                value = Queue(name = "\${edu_sharing.queue.edu_html.name}", durable = "false"),
                exchange = Exchange(name = "\${edu_sharing.queue.topicExchange}", type = "topic"),
                key = ["\${edu_sharing.queue.edu_html.key}"]
            )
        ], containerFactory = "singlePrefetchConnectionFactory"
    )
    fun receiveMessage(message: RenderingJobMessage) {
        val jobEntry = renderingJobRepository.findByIdOrNull(ObjectId(message.id)) ?: return
        jobEntry.status = JobStatus.PROCESSING
        val subJob = SubJob(
            status = JobStatus.PROCESSING,
            routingKey = jobRoutingKey,
            parent = jobEntry
        )
        subJobRepository.save(subJob)
        var success = true
        try {
            eduHtmlService.unzipArchive(mapper.renderingJobToCacheObject(jobEntry))
            subJob.message = eduHtmlService.getObjectLink(jobEntry.esObjectId)?.link ?: ""
        } catch (exception: Exception) {
            log.warn(exception.message)
            subJob.status = JobStatus.FAILED
            success = false
        } finally {
            subJob.status = if (success) JobStatus.FINISHED else JobStatus.FAILED
            subJobRepository.save(subJob)
        }
        mainJobLogic.processMainJob(jobEntry.id.toString())
    }
}