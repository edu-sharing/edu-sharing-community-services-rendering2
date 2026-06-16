package org.edu_sharing.rendering.modules.sodix

import tools.jackson.databind.ObjectMapper
import org.edu_sharing.rendering.core.ErrorStrings.GENERIC_CONVERSION_ERROR
import org.edu_sharing.rendering.core.annotation.ConditionalOnConverter
import org.edu_sharing.rendering.modules.ModuleRegistry
import org.edu_sharing.rendering.renderingJob.MainJobLogic
import org.edu_sharing.rendering.renderingJob.entity.RenderingJobStatus
import org.edu_sharing.rendering.renderingJob.entity.SubJobStatus
import org.edu_sharing.rendering.renderingJob.repository.RenderingJobRepository
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.Exchange
import org.springframework.amqp.rabbit.annotation.Queue
import org.springframework.amqp.rabbit.annotation.QueueBinding
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClientResponseException

@Component
@ConditionalOnConverter
class SodixReceiver(
    private val sodixService: SodixApiCallerService,
    private val renderingJobRepository: RenderingJobRepository,
    private val subJobRepository: SubJobRepository,
    private val mainJobLogic: MainJobLogic,
    private val moduleRegistry: ModuleRegistry
) {
    private val log = LoggerFactory.getLogger(SodixReceiver::class.java)

    @RabbitListener(
        bindings = [
            QueueBinding(
                value = Queue(name = $$"${app.queue.sodix.name}", durable = "false"),
                exchange = Exchange(name = $$"${app.queue.topicExchange}", type = "topic"),
                key = [$$"${app.queue.sodix.key}"]
            )
        ], containerFactory = "singlePrefetchConnectionFactory"
    )
    fun receiveMessage(message: SodixJobMessage) {
        log.debug("Received Sodix job message for jobId ${message.id}, nodeId ${message.nodeId}, identifier ${message.identifier}")
        var jobEntry = mainJobLogic.getMainJobEntry(message.id)
        if (jobEntry == null || jobEntry.subJobs.isEmpty()) {
            log.error(if (jobEntry == null) "No job entry with id {}"
            else "Job entry with id {} has no sub jobs" , message.id)
            return
        }
        log.debug("Processing Sodix job ${message.id}, isPaidMedia ${message.isPaidMedia}")
        jobEntry.status = RenderingJobStatus.PROCESSING
        jobEntry = renderingJobRepository.save(jobEntry)
        var playoutUrlSubJob = jobEntry.subJobs.first { it.quality == 0}
        playoutUrlSubJob.status = SubJobStatus.PROCESSING
        playoutUrlSubJob = subJobRepository.save(playoutUrlSubJob)
        try {
            val (playoutUrl, downloadUrl) = sodixService.getContentUrl(
                sodixJobMessage = message,
                module = moduleRegistry.getRenderModule(jobEntry.module),
                repoId = jobEntry.repoId,
            )
            log.debug("Sodix content URL retrieved for job ${message.id}, marking sub-job as FINISHED")
            playoutUrlSubJob.status = SubJobStatus.FINISHED
            playoutUrlSubJob.message = playoutUrl
            if (downloadUrl != null) {
                playoutUrlSubJob.additionalData = mapOf("downloadUrl" to downloadUrl)
            }
            subJobRepository.save(playoutUrlSubJob)
        } catch (exception: Exception) {
            log.error(exception.message, exception)
            val objectMapper = ObjectMapper()
            var userMessage = GENERIC_CONVERSION_ERROR
            if (exception is WebClientResponseException && exception.statusCode == HttpStatus.BAD_GATEWAY) {
                userMessage = try {
                    val jsonNode = objectMapper.readTree(exception.responseBodyAsString)
                    jsonNode.path("error").asString(GENERIC_CONVERSION_ERROR)
                } catch (e: Exception) {
                    log.error(e.message, e)
                    GENERIC_CONVERSION_ERROR
                }
            }
            jobEntry.errorMessage = userMessage
            renderingJobRepository.save(jobEntry)
            playoutUrlSubJob.status = SubJobStatus.FAILED
            playoutUrlSubJob.errorMessage = userMessage
            subJobRepository.save(playoutUrlSubJob)
        }
        mainJobLogic.processMainJob(message.id)
    }
}
