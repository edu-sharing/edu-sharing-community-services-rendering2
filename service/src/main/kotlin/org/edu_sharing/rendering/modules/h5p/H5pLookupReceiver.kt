package org.edu_sharing.rendering.modules.h5p

import org.edu_sharing.rendering.config.AppInfo
import org.edu_sharing.rendering.config.H5P_BASE_PATH
import org.edu_sharing.rendering.core.annotation.ConditionalOnConverter
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.modules.h5p.lumi.LumiContentManagementService
import org.edu_sharing.rendering.renderingJob.MainJobLogic
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.RenderingJobStatus
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.edu_sharing.rendering.renderingJob.entity.SubJobStatus
import org.edu_sharing.rendering.renderingJob.queue.RenderingJobMessage
import org.edu_sharing.rendering.renderingJob.repository.RenderingJobRepository
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.edu_sharing.rendering.utils.combinePath
import org.slf4j.LoggerFactory
import org.springframework.amqp.core.AmqpTemplate
import org.springframework.amqp.rabbit.annotation.Exchange
import org.springframework.amqp.rabbit.annotation.Queue
import org.springframework.amqp.rabbit.annotation.QueueBinding
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * First stage of the H5P job flow: asks lumi whether the node revision is already imported.
 *
 * This is a plain fan-out queue — a lookup is one read-only content-id query, so many can run at once. Only a
 * miss is handed on to [H5pImportReceiver]'s single-active import queue, so concurrent renders of content lumi
 * already holds never queue up behind an import.
 */
@ConditionalOnConverter
@Component
class H5pLookupReceiver(
    private val mainJobLogic: MainJobLogic,
    private val renderingJobRepository: RenderingJobRepository,
    private val subJobRepository: SubJobRepository,
    private val lumiContentManagementService: LumiContentManagementService,
    private val amqpTemplate: AmqpTemplate,
    private val mapper: Mapper,
    private val appInfo: AppInfo
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Value($$"${app.queue.topicExchange}")
    lateinit var topicExchangeName: String

    @Value($$"${app.queue.h5p.key}")
    lateinit var importRoutingKey: String

    @RabbitListener(
        bindings = [
            QueueBinding(
                // No x-single-active-consumer here (unlike the import queue): lookups are read-only and
                // must fan out across all consumers and pods.
                value = Queue(name = "#{h5pLookupQueueProperties.name}", durable = "true"),
                exchange = Exchange(name = "#{queueProperties.topicExchange}", type = "topic"),
                key = ["#{h5pLookupQueueProperties.key}"]
            )
        ],
        containerFactory = "queueListenerContainerFactory",
        concurrency = "#{h5pLookupQueueProperties.effectiveConcurrency}"
    )
    fun receiveMessage(message: RenderingJobMessage) {
        log.debug("H5P lookup message received: jobId={}", message.id)
        val jobEntry = mainJobLogic.getMainJobEntry(message.id)
        if (jobEntry == null) {
            log.error("No matching job entry with id {}", message.id)
            return
        }
        // RabbitMQ is at-least-once: guard against re-processing a redelivered message. An H5P job carries
        // exactly one sub-job, so its status is the stage marker for the whole job.
        val subJob = jobEntry.subJobs.firstOrNull()
        if (subJob == null) {
            log.error("Job entry with id {} has no sub jobs", message.id)
            return
        }
        if (subJob.status != SubJobStatus.QUEUED) {
            log.debug("H5P job {} already past the lookup stage (sub-job {}); dropping redelivered message",
                message.id, subJob.status)
            return
        }
        subJob.status = SubJobStatus.PROCESSING
        subJob.processingStartedDate = Instant.now()
        jobEntry.status = RenderingJobStatus.PROCESSING
        jobEntry.processingStartedTimestamp = System.currentTimeMillis()
        renderingJobRepository.save(jobEntry)
        val processingSubJob = subJobRepository.save(subJob)

        // Same key the import stage looks up with (H5pUploadService.getLumiId) — go through the mapper so
        // the two stages cannot drift apart.
        val cacheObject = mapper.renderingJobToCacheObject(jobEntry)
        val contentId = try {
            lumiContentManagementService.getContentId(cacheObject.nodeId, cacheObject.hash)
        } catch (exception: Exception) {
            // Not a job failure: the import stage repeats the lookup with the repository's own timeout
            // before it uploads anything.
            log.warn("H5P lookup at lumi failed for nodeId={}, handing over to the import queue: {}",
                cacheObject.nodeId, exception.message)
            null
        }

        if (contentId == null) {
            handOverToImport(jobEntry, processingSubJob)
            return
        }
        log.info("H5P already imported, skipping the import queue. Content id: {}", contentId)
        processingSubJob.status = SubJobStatus.FINISHED
        processingSubJob.finishedDate = Instant.now()
        processingSubJob.message = appInfo.public.url.combinePath(H5P_BASE_PATH, contentId)
        subJobRepository.save(processingSubJob)
        mainJobLogic.processMainJob(message.id)
    }

    /**
     * Re-routes the sub-job to the import queue instead of creating a second one: the main job then stays
     * non-terminal (`MainJobLogic.processMainJob` defers while a sub-job is QUEUED/PROCESSING) and the
     * rewritten `routingKey` is what makes the client's queue position and the stale-job reaper's timeout
     * reflect the import stage. QUEUED is deliberate — the reaper never times out a queued sub-job, so a
     * legitimate import backlog is not reaped.
     */
    private fun handOverToImport(jobEntry: RenderingJob, subJob: SubJob) {
        subJob.status = SubJobStatus.QUEUED
        // Reset so the eventual processingStartedDate (set by H5pImportReceiver) reflects when the
        // import stage actually starts - not this lookup's brief PROCESSING window - and the
        // "queued time" the admin UI derives from createdDate/processingStartedDate keeps covering
        // the whole wait, including this hand-off.
        subJob.processingStartedDate = null
        subJob.routingKey = importRoutingKey
        subJobRepository.save(subJob)
        amqpTemplate.convertAndSend(topicExchangeName, importRoutingKey, RenderingJobMessage(jobEntry.id.toString()))
        log.debug("H5P job {} handed over to the import queue on routingKey={}", jobEntry.id, importRoutingKey)
    }
}
