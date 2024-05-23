package org.edu_sharing.rendering.processing.eduhtml

import org.edu_sharing.rendering.config.annotation.ConditionalOnConverter
import org.edu_sharing.rendering.dto.mapper.Mapper
import org.edu_sharing.rendering.dto.queue.RenderingJobMessage
import org.edu_sharing.rendering.entity.JobStatus
import org.edu_sharing.rendering.modules.html.EduHtmlService
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
class EduHtmlReceiver (
    private val eduHtmlService: EduHtmlService,
    private val eduHtmlConversionService: EduHtmlConversionService,
    private val subJobRepository: SubJobRepository,
    private val mainJobLogic: MainJobLogic,
    private val mapper: Mapper
    ) {

    private val log = LoggerFactory.getLogger(javaClass)

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
        log.debug("Message received: {}", message)
        val jobEntry = mainJobLogic.getMainJobEntry(message.id)
        if (jobEntry == null || jobEntry.subJobs.isEmpty()) {
            log.error(if (jobEntry == null) "No job entry with id {}"
                else "Job entry with id {} has no sub jobs" , message.id)
            return
        }
        log.debug("Job retrieved: {}", jobEntry)
        jobEntry.status = JobStatus.PROCESSING
        val subJob = jobEntry.subJobs[0]
        var success = true
        try {
            eduHtmlConversionService.cacheData(mapper.renderingJobToCacheObject(jobEntry))
            subJob.message = eduHtmlService.getObjectLink(jobEntry.esObjectId).link
        } catch (exception: Exception) {
            log.error("Job id ${message.id} failed with exception: ${exception.message}")
            success = false
        } finally {
            subJob.status = if (success) JobStatus.FINISHED else JobStatus.FAILED
            subJobRepository.save(subJob)
        }
        mainJobLogic.processMainJob(jobEntry.id.toString())
    }
}
