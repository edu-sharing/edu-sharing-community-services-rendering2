package org.edu_sharing.rendering.renderingJob.queue

import org.bson.types.ObjectId
import org.edu_sharing.rendering.core.ErrorStrings
import org.edu_sharing.rendering.renderingJob.entity.RenderingJobStatus
import org.edu_sharing.rendering.renderingJob.repository.RenderingJobRepository
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.connection.CorrelationData
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.stereotype.Component

/**
 * [CorrelationData] that maps a publisher confirm back to the job the message belongs to. The template's
 * confirm callback (see [QueueConfig.amqpTemplate]) invokes [onUnconfirmed] when the broker nacks the publish
 * or returns the message as unroutable.
 */
class JobCorrelationData(
    val jobId: ObjectId,
    private val onUnconfirmed: (reason: String) -> Unit,
) : CorrelationData(jobId.toString()) {
    fun unconfirmed(reason: String) = onUnconfirmed(reason)
}

/**
 * Central publish helper for messages that belong to a persisted rendering job whose creation is
 * deduplicated through the `activeJobPerNodeHash` unique index.
 *
 * Without it, a failed publish (synchronous exception, broker nack, unroutable message) leaves a QUEUED job
 * that no message will ever pick up, and [org.edu_sharing.rendering.renderingJob.MainJobCreationService.getExistingJobId]
 * keeps handing out that dead job until the reaper's QUEUED timeout. This helper compensates instead: the
 * job is set to FAILED immediately, which frees the dedup index so the next request can create a new job.
 *
 * Other modules still call `amqpTemplate.convertAndSend` directly (see the report of RENDER-178 / the list in
 * service/CLAUDE.md); they can adopt [publishForJob] incrementally.
 */
@Component
class JobMessagePublisher(
    private val rabbitTemplate: RabbitTemplate,
    private val jobRepository: RenderingJobRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Publishes [message] and, if the publish fails, marks job [jobId] FAILED before rethrowing. A later
     * broker nack / unroutable return (reported asynchronously by the confirm callback) fails the job as well.
     */
    fun publishForJob(exchange: String, routingKey: String, message: Any, jobId: ObjectId) {
        val correlation = JobCorrelationData(jobId) { reason -> failJob(jobId, reason) }
        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, message, correlation)
        } catch (e: Exception) {
            log.error("Publishing message for job {} failed; failing the job so it cannot block its node", jobId, e)
            failJob(jobId, e.message ?: e.javaClass.simpleName)
            throw e
        }
    }

    /**
     * Targeted (version-free) update: the in-memory entity of the caller may be stale, and this can also run
     * on a connection thread of the broker client where a failure must never escape.
     */
    internal fun failJob(jobId: ObjectId, reason: String) {
        try {
            log.warn("Marking job {} FAILED because its queue message was not delivered: {}", jobId, reason)
            jobRepository.updateErrorMessageWithoutVersion(jobId, ErrorStrings.ERROR_PROCESSING_JOB)
            jobRepository.updateStatusWithoutVersion(jobId, RenderingJobStatus.FAILED)
        } catch (e: Exception) {
            // Last line of defence is the reaper's QUEUED pass.
            log.error("Could not mark job {} FAILED after a failed publish; the stale-job reaper will resolve it", jobId, e)
        }
    }
}
