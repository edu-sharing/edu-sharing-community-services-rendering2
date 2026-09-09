package org.edu_sharing.rendering.modules.onyx

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
import java.time.Instant

@Component
@ConditionalOnConverter
class OnyxReceiver(
    private val mainJobLogic: MainJobLogic,
    private val renderingJobRepository: RenderingJobRepository,
    private val mapper: Mapper,
    private val onyxUploadService: OnyxUploadService,
    private val subJobRepository: SubJobRepository
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    @RabbitListener(
        bindings = [
            QueueBinding(
                value = Queue(name = "#{onyxQueueProperties.name}", durable = "false"),
                exchange = Exchange(name = "#{queueProperties.topicExchange}", type = "topic"),
                key = ["#{onyxQueueProperties.key}"]
            )
        ], containerFactory = "queueListenerContainerFactory",
        concurrency = "#{onyxQueueProperties.effectiveConcurrency}"
    )
    fun receiveMessage(message: RenderingJobMessage) {
        log.debug("Received Onyx job message for jobId ${message.id}")
        val jobEntry = mainJobLogic.getMainJobEntry(message.id)
        if (jobEntry == null || jobEntry.subJobs.isEmpty()) {
            log.error(if (jobEntry == null) "No job entry with id {}"
            else "Job entry with id {} has no sub jobs" , message.id)
            return
        }
        val subJob = jobEntry.subJobs.first()
        // RabbitMQ is at-least-once: guard against re-processing a redelivered message (e.g. the ack for
        // an already-finished upload was lost), which would re-run the onyx test upload.
        if (subJob.status != SubJobStatus.QUEUED) {
            log.debug("Onyx job {} already past QUEUED (sub-job {}); dropping redelivered message", message.id, subJob.status)
            return
        }

        val cacheObject = mapper.renderingJobToCacheObject(jobEntry)
        try {
            log.debug("Processing Onyx job ${message.id} for nodeId ${cacheObject.nodeId}")
            subJobRepository.updateStatusWithoutVersion(subJob.id, SubJobStatus.PROCESSING)
            renderingJobRepository.updateStatusWithoutVersion(jobEntry.id, RenderingJobStatus.PROCESSING)
            // Mirror what the versionless updates above just wrote, so the full save below (of a
            // subJob instance fetched before those updates) doesn't clobber it back to null.
            subJob.processingStartedDate = Instant.now()
            subJob.message = onyxUploadService.uploadTest(cacheObject)
            log.debug("Onyx upload finished for job ${message.id}, sub-job marked FINISHED")
            subJob.status = SubJobStatus.FINISHED
            subJob.finishedDate = Instant.now()
            subJobRepository.save(subJob)
        } catch (exception: Exception) {
            log.error("Onyx upload failed with exception: ${exception.message}", exception)
            subJob.status = SubJobStatus.FAILED
            subJob.finishedDate = Instant.now()
            subJob.errorMessage = GENERIC_CONVERSION_ERROR
            subJobRepository.save(subJob)
        } finally {
            mainJobLogic.processMainJob(message.id)
        }
    }
}
