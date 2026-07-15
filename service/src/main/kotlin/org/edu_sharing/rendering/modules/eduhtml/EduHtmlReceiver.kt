package org.edu_sharing.rendering.modules.eduhtml

import org.edu_sharing.rendering.core.ErrorStrings.GENERIC_CONVERSION_ERROR
import org.edu_sharing.rendering.core.annotation.ConditionalOnConverter
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.renderingJob.MainJobLogic
import org.edu_sharing.rendering.renderingJob.entity.RenderingJobStatus
import org.edu_sharing.rendering.renderingJob.entity.SubJobStatus
import org.edu_sharing.rendering.renderingJob.queue.RenderingJobMessage
import org.edu_sharing.rendering.renderingJob.repository.RenderingJobRepository
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.Exchange
import org.springframework.amqp.rabbit.annotation.Queue
import org.springframework.amqp.rabbit.annotation.QueueBinding
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

@ConditionalOnConverter
@Component
class EduHtmlReceiver(
    private val eduHtmlService: EduHtmlService,
    private val eduHtmlConversionService: EduHtmlConversionService,
    private val subJobRepository: SubJobRepository,
    private val mainJobLogic: MainJobLogic,
    private val mapper: Mapper,
    private val jobRepository: RenderingJobRepository,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @RabbitListener(
        bindings = [
            QueueBinding(
                value = Queue(name = $$"${app.queue.eduHtml.name}", durable = "false"),
                exchange = Exchange(name = $$"${app.queue.topicExchange}", type = "topic"),
                key = [$$"${app.queue.eduHtml.key}"]
            )
        ], containerFactory = "singlePrefetchConnectionFactory",
        concurrency = $$"${app.queue.eduHtml.concurrency:1}"
    )
    fun receiveMessage(message: RenderingJobMessage) {
        log.debug("Message received: {}", message)
        val jobEntry = mainJobLogic.getMainJobEntry(message.id)
        if (jobEntry == null || jobEntry.subJobs.isEmpty()) {
            log.error(
                if (jobEntry == null) "No job entry with id {}"
                else "Job entry with id {} has no sub jobs", message.id
            )
            return
        }
        log.debug("Job retrieved: {}", jobEntry)
        jobRepository.updateStatusWithoutVersion(jobEntry.id, RenderingJobStatus.PROCESSING)
        var subJob = jobEntry.subJobs[0]
        subJob = subJobRepository.save(subJob)
        var success = true
        try {
            val cacheObject = mapper.renderingJobToCacheObject(jobEntry)
            val candidates = eduHtmlService.entryCandidates(subJob.additionalData?.get(EduHtmlService.MAIN_ENTITY_KEY))
            eduHtmlConversionService.cacheData(cacheObject, candidates)
            subJob.message = eduHtmlService.getObjectLink(cacheObject, candidates).link
        } catch (exception: Exception) {
            log.error("Job id ${message.id} failed with exception: ${exception.message}", exception)
            subJob.errorMessage = GENERIC_CONVERSION_ERROR
            success = false
        } finally {
            subJob.status = if (success) SubJobStatus.FINISHED else SubJobStatus.FAILED
            subJobRepository.save(subJob)
        }
        mainJobLogic.processMainJob(jobEntry.id.toString())
    }
}
