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
import org.springframework.amqp.rabbit.annotation.Argument
import org.springframework.amqp.rabbit.annotation.Exchange
import org.springframework.amqp.rabbit.annotation.Queue
import org.springframework.amqp.rabbit.annotation.QueueBinding
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

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
            // Single Active Consumer: moodle can import only one document at a time, so the
            // broker must route moodle jobs to exactly one consumer cluster-wide. Per-instance
            // concurrency=1 alone is not enough — with N pods, N competing consumers would
            // each process a job in parallel. x-single-active-consumer keeps a single consumer
            // active across all pods; the others stay on standby and take over only on failover.
            QueueBinding(
                value = Queue(name = $$"${app.queue.moodle.name}", durable = "false"),
                exchange = Exchange(name = $$"${app.queue.topicExchange}", type = "topic"),
                arguments = [Argument(
                    name = "x-single-active-consumer",
                    value = "true",
                    type = "java.lang.Boolean"
                )],
                key = [$$"${app.queue.moodle.key}"]
            )
        ], containerFactory = "queueListenerContainerFactory",
        concurrency = $$"${app.queue.moodle.consumersPerQueue:1}"
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
        subJob.status = SubJobStatus.PROCESSING
        jobEntry.status = RenderingJobStatus.PROCESSING
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
        } catch (exception: Exception) {
            log.error("Job id ${message.id} failed with exception: ${exception.message}", exception)
            subJob.status = SubJobStatus.FAILED
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
