package org.edu_sharing.rendering.renderingJob.queue

import org.bson.types.ObjectId
import org.edu_sharing.rendering.core.ErrorStrings
import org.edu_sharing.rendering.renderingJob.repository.RenderingJobRepository
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.slf4j.LoggerFactory
import org.springframework.amqp.core.MessagePostProcessor
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
 * Central publish helper: every module that publishes a message belonging to a persisted rendering job goes
 * through it, in one of two variants.
 *
 * Without it, a failed publish (synchronous exception, broker nack, unroutable message) leaves a QUEUED job
 * that no message will ever pick up, and the deduplicated-job lookup keeps handing out that dead job until the
 * reaper's QUEUED timeout. This helper compensates instead:
 * - [publishForJob]: the message starts a whole main job (one message per main job: Sodix, Omega, DDB, Onyx,
 *   Moodle, EduHtml, H5P lookup/import, Binder upload, the generic job queue). The main job is set FAILED
 *   immediately, which also frees the `activeJobPerNodeHash` dedup index.
 * - [publishForSubJobs]: the message only carries some sub-jobs of a main job (image/document/jupyter/video/audio;
 *   several messages per main job). Failing the whole main job would be too coarse and could clobber a finished
 *   part, so only the sub-jobs of the message are failed (while still QUEUED) and the main job is then resolved
 *   by the module's own main-job logic (which can yield FAILED, PARTIALLY_FAILED or keep waiting).
 *
 * Deliberately not routed through here: pure broadcasts without a persisted job (CorsSyncService,
 * RegistrationCacheBroadcaster) and the optional Binder preview message (best effort by design).
 */
@Component
class JobMessagePublisher(
    private val rabbitTemplate: RabbitTemplate,
    private val jobRepository: RenderingJobRepository,
    private val subJobRepository: SubJobRepository,
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
     * Publishes a message that stands for the sub-jobs [subJobIds] of main job [mainJobId]. If the publish fails
     * (exception, nack, unroutable) those sub-jobs are set FAILED - only while still QUEUED, so a sub-job a
     * consumer already took is left alone - and [resolveMainJob] (the module's `processMainJob`) aggregates the
     * main job. A synchronous exception is rethrown afterwards.
     */
    fun publishForSubJobs(
        exchange: String,
        routingKey: String,
        message: Any,
        mainJobId: ObjectId,
        subJobIds: Collection<ObjectId>,
        resolveMainJob: (mainJobId: String) -> Unit,
        postProcessor: MessagePostProcessor? = null,
    ) {
        val correlation = JobCorrelationData(mainJobId) { reason ->
            failSubJobs(mainJobId, subJobIds, reason, resolveMainJob)
        }
        try {
            if (postProcessor == null) {
                rabbitTemplate.convertAndSend(exchange, routingKey, message, correlation)
            } else {
                rabbitTemplate.convertAndSend(exchange, routingKey, message, postProcessor, correlation)
            }
        } catch (e: Exception) {
            log.error("Publishing message for sub-jobs {} of job {} failed; failing them", subJobIds, mainJobId, e)
            failSubJobs(mainJobId, subJobIds, e.message ?: e.javaClass.simpleName, resolveMainJob)
            throw e
        }
    }

    /**
     * Targeted (version-free) and atomic: the in-memory entity of the caller may be stale, a job that is already
     * terminal must not be overwritten, and this can also run on a connection thread of the broker client where a
     * failure must never escape.
     */
    internal fun failJob(jobId: ObjectId, reason: String) {
        try {
            if (jobRepository.failIfActive(jobId, ErrorStrings.ERROR_PROCESSING_JOB)) {
                log.warn("Marked job {} FAILED because its queue message was not delivered: {}", jobId, reason)
            } else {
                log.debug("Job {} is already terminal (or gone); not failing it for an undelivered message: {}", jobId, reason)
            }
        } catch (e: Exception) {
            // Last line of defence is the reaper's QUEUED pass.
            log.error("Could not mark job {} FAILED after a failed publish; the stale-job reaper will resolve it", jobId, e)
        }
    }

    internal fun failSubJobs(
        mainJobId: ObjectId,
        subJobIds: Collection<ObjectId>,
        reason: String,
        resolveMainJob: (String) -> Unit,
    ) {
        try {
            val failed = subJobIds.count { subJobRepository.failIfQueued(it, ErrorStrings.GENERIC_CONVERSION_ERROR) }
            log.warn("Failed {} of {} sub-job(s) of job {} because their queue message was not delivered: {}",
                failed, subJobIds.size, mainJobId, reason)
            if (failed > 0) {
                resolveMainJob(mainJobId.toString())
            }
        } catch (e: Exception) {
            // Last line of defence is the reaper's sub-job / main-job passes.
            log.error("Could not fail the sub-jobs of job {} after a failed publish; the stale-job reaper will resolve it", mainJobId, e)
        }
    }
}
