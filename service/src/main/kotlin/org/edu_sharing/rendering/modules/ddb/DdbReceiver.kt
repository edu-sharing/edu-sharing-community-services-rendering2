package org.edu_sharing.rendering.modules.ddb

import org.edu_sharing.rendering.core.annotation.ConditionalOnConverter
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.renderingJob.MainJobLogic
import org.edu_sharing.rendering.renderingJob.entity.RenderingJobStatus
import org.edu_sharing.rendering.renderingJob.queue.AsyncAckDispatcher
import org.edu_sharing.rendering.renderingJob.repository.RenderingJobRepository
import com.rabbitmq.client.Channel
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.Exchange
import org.springframework.amqp.rabbit.annotation.Queue
import org.springframework.amqp.rabbit.annotation.QueueBinding
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.amqp.support.AmqpHeaders
import org.springframework.messaging.handler.annotation.Header
import org.springframework.stereotype.Component

@Component
@ConditionalOnConverter
class DdbReceiver(
    val mainJobLogic: MainJobLogic,
    val mapper: Mapper,
    val ddbApiService: DdbApiService,
    val renderingJobRepository: RenderingJobRepository,
    val asyncAckDispatcher: AsyncAckDispatcher
) {
    private val log = LoggerFactory.getLogger(this.javaClass)

    @RabbitListener(
        bindings = [
            QueueBinding(
                value = Queue(name = "#{ddbQueueProperties.name}", durable = "false"),
                exchange = Exchange(name = "#{queueProperties.topicExchange}", type = "topic"),
                key = ["#{ddbQueueProperties.key}"]
            )
        ], containerFactory = "ddbRemoteListenerContainerFactory",
        concurrency = "#{ddbQueueProperties.effectiveConcurrency}"
    )
    fun receiveMessage(
        message: DdbJobMessage,
        channel: Channel,
        @Header(AmqpHeaders.DELIVERY_TAG) deliveryTag: Long,
        @Header(AmqpHeaders.CONSUMER_QUEUE) queue: String,
    ) {
        asyncAckDispatcher.dispatch(channel, deliveryTag, queue) { process(message) }
    }

    private fun process(message: DdbJobMessage) {
        log.debug("Received DDB job message for jobId ${message.id}, remoteId ${message.remoteId}")
        val mainJob = mainJobLogic.getMainJobEntry(message.id)
        if (mainJob == null) {
            log.error("${this.javaClass.simpleName} received message with unknown job id ${message.id}")
            return
        }
        log.debug("Processing DDB job ${message.id}, nodeId ${mainJob.esObjectId}")
        renderingJobRepository.updateStatusWithoutVersion(mainJob.id, RenderingJobStatus.PROCESSING)
        try {
            ddbApiService.process(message.remoteId, mainJob)
        } catch (exception: Exception) {
            // DdbApiService handles the sub-job status itself; this catch is the safety net for
            // failures thrown past that handling (infra/DB errors) so the main job never stays
            // stuck in PROCESSING — an orphaned non-terminal job would poison re-rendering of the
            // node until the 8-day TTL (see MainJobCreationService.getExistingJobId reuse).
            log.error("Error processing DDB job ${message.id}, marking as FAILED", exception)
            renderingJobRepository.updateStatusWithoutVersion(mainJob.id, RenderingJobStatus.FAILED)
        } finally {
            // Always reconcile: on success this aggregates the sub-job states; if a sub-job was left
            // non-terminal it defers and the FAILED set above stands.
            mainJobLogic.processMainJob(mainJob.id.toString())
        }
    }
}
