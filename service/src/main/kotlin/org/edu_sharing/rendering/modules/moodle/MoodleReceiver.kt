package org.edu_sharing.rendering.modules.moodle

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
import org.springframework.stereotype.Component
import java.time.Instant

@Component
@ConditionalOnConverter
class MoodleReceiver (
    private val moodleService: MoodleUploadService,
    private val renderingJobRepository: RenderingJobRepository,
    private val subJobRepository: SubJobRepository,
    private val mainJobLogic: MainJobLogic,
    private val moduleRegistry: ModuleRegistry
) {
    private val log = LoggerFactory.getLogger(MoodleReceiver::class.java)

    @RabbitListener(
        bindings = [
            // STANDARD fan-out queue on the shared listener factory: app.queue.moodle.concurrency consumers
            // per pod, so moodle imports run in parallel across the cluster (each import mostly waits on the
            // remote moodle, including a restore poll of up to 10 minutes).
            QueueBinding(
                value = Queue(name = "#{moodleQueueProperties.name}", durable = "false"),
                exchange = Exchange(name = "#{queueProperties.topicExchange}", type = "topic"),
                key = ["#{moodleQueueProperties.key}"]
            )
        ], containerFactory = "queueListenerContainerFactory",
        concurrency = "#{moodleQueueProperties.effectiveConcurrency}"
    )
    fun receiveMessage(message: MoodleJobMessage) {
        log.debug("Received Moodle job message for jobId ${message.id}, nodeId ${message.nodeId}")
        var jobEntry = mainJobLogic.getMainJobEntry(message.id)
        if (jobEntry == null || jobEntry.subJobs.isEmpty()) {
            log.error(if (jobEntry == null) "No job entry with id {}"
            else "Job entry with id {} has no sub jobs" , message.id)
            return
        }
        var subJob = jobEntry.subJobs.first()
        // RabbitMQ is at-least-once: guard against re-processing a redelivered message (e.g. the ack for
        // an already-finished moodle import was lost), which would re-run the moodle course import.
        if (subJob.status != SubJobStatus.QUEUED) {
            log.debug("Moodle job {} already past QUEUED (sub-job {}); dropping redelivered message", message.id, subJob.status)
            return
        }
        subJob.status = SubJobStatus.PROCESSING
        subJob.processingStartedDate = Instant.now()
        jobEntry.status = RenderingJobStatus.PROCESSING
        jobEntry.processingStartedTimestamp = System.currentTimeMillis()
        jobEntry = renderingJobRepository.save(jobEntry)
        subJob = subJobRepository.save(subJob)
        log.debug("Processing Moodle job ${message.id}, calling upload service for nodeId ${message.nodeId}")
        try {
            val url = moodleService.getUrl(
                moodleJobMessage = message,
                module = moduleRegistry.getRenderModule(jobEntry.module),
                repoId = jobEntry.repoId,
            )
            log.debug("Moodle URL obtained for job ${message.id}, marking sub-job as FINISHED")
            subJob.message = url.first
            subJob.additionalData = mapOf("linkUrl" to url.second)
            subJob.status = SubJobStatus.FINISHED
            subJob.finishedDate = Instant.now()
        } catch (exception: Exception) {
            log.error("Job id ${message.id} failed with exception: ${exception.message}", exception)
            subJob.status = SubJobStatus.FAILED
            subJob.finishedDate = Instant.now()
            if (exception is MoodleUploadException) {
                subJob.errorMessage = exception.publicMessage
                jobEntry.errorMessage = exception.publicMessage
                renderingJobRepository.save(jobEntry)
            } else {
                subJob.errorMessage = GENERIC_CONVERSION_ERROR
            }
        }
        subJobRepository.save(subJob)
        mainJobLogic.processMainJob(message.id)
    }
}
