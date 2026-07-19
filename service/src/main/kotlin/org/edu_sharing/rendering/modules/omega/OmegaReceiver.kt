package org.edu_sharing.rendering.modules.omega

import tools.jackson.databind.ObjectMapper
import org.edu_sharing.rendering.core.ErrorStrings.GENERIC_CONVERSION_ERROR
import org.edu_sharing.rendering.core.annotation.ConditionalOnConverter
import org.edu_sharing.rendering.modules.ModuleRegistry
import org.edu_sharing.rendering.renderingJob.MainJobLogic
import org.edu_sharing.rendering.renderingJob.entity.RenderingJobStatus
import org.edu_sharing.rendering.renderingJob.entity.SubJobStatus
import org.edu_sharing.rendering.renderingJob.repository.RenderingJobRepository
import org.edu_sharing.rendering.renderingJob.queue.AsyncAckDispatcher
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import com.rabbitmq.client.Channel
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.Exchange
import org.springframework.amqp.rabbit.annotation.Queue
import org.springframework.amqp.rabbit.annotation.QueueBinding
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.amqp.support.AmqpHeaders
import org.springframework.http.HttpStatus
import org.springframework.messaging.handler.annotation.Header
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClientResponseException

@Component
@ConditionalOnConverter
class OmegaReceiver(
    private val omegaService: OmegaApiCallerService,
    private val renderingJobRepository: RenderingJobRepository,
    private val subJobRepository: SubJobRepository,
    private val mainJobLogic: MainJobLogic,
    private val moduleRegistry: ModuleRegistry,
    private val asyncAckDispatcher: AsyncAckDispatcher
) {
    private val log = LoggerFactory.getLogger(OmegaReceiver::class.java)

    @RabbitListener(
        bindings = [
            QueueBinding(
                value = Queue(name = "#{omegaQueueProperties.name}", durable = "false"),
                exchange = Exchange(name = "#{queueProperties.topicExchange}", type = "topic"),
                key = ["#{omegaQueueProperties.key}"]
            )
        ], containerFactory = "omegaRemoteListenerContainerFactory",
        concurrency = "#{omegaQueueProperties.effectiveConcurrency}"
    )
    fun receiveMessage(
        message: OmegaJobMessage,
        channel: Channel,
        @Header(AmqpHeaders.DELIVERY_TAG) deliveryTag: Long,
        @Header(AmqpHeaders.CONSUMER_QUEUE) queue: String,
    ) {
        asyncAckDispatcher.dispatch(channel, deliveryTag, queue) { process(message) }
    }

    private fun process(message: OmegaJobMessage) {
        log.debug("Received Omega job message for jobId ${message.id}, nodeId ${message.nodeId}, identifier ${message.identifier}")
        var jobEntry = mainJobLogic.getMainJobEntry(message.id)
        if (jobEntry == null || jobEntry.subJobs.isEmpty()) {
            log.error(if (jobEntry == null) "No job entry with id {}"
            else "Job entry with id {} has no sub jobs" , message.id)
            return
        }
        log.debug("Processing Omega job ${message.id}")
        jobEntry.status = RenderingJobStatus.PROCESSING
        jobEntry = renderingJobRepository.save(jobEntry)
        var streamUrlSubJob = jobEntry.subJobs.first { it.quality == 0}
        streamUrlSubJob.status = SubJobStatus.PROCESSING
        streamUrlSubJob = subJobRepository.save(streamUrlSubJob)
        try {
            val (streamUrl, downloadUrl) = omegaService.getContentUrl(
                omegaJobMessage = message,
                module = moduleRegistry.getRenderModule(jobEntry.module),
                repoId = jobEntry.repoId,
            )
            log.debug("Omega content URL retrieved for job ${message.id}, marking sub-job as FINISHED")
            streamUrlSubJob.status = SubJobStatus.FINISHED
            streamUrlSubJob.message = streamUrl
            if (downloadUrl != null) {
                streamUrlSubJob.additionalData = mapOf("downloadUrl" to downloadUrl)
            }
            subJobRepository.save(streamUrlSubJob)
        } catch (exception: Exception) {
            log.error(exception.message, exception)
            val objectMapper = ObjectMapper()
            var userMessage = exception.message ?: GENERIC_CONVERSION_ERROR
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
            streamUrlSubJob.status = SubJobStatus.FAILED
            streamUrlSubJob.errorMessage = userMessage
            subJobRepository.save(streamUrlSubJob)
        }
        mainJobLogic.processMainJob(message.id)
    }
}
