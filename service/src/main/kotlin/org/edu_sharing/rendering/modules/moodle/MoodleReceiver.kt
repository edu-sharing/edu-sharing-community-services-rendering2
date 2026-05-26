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
            QueueBinding(
                value = Queue(name = "\${app.queue.moodle.name}", durable = "true"),
                exchange = Exchange(name = "\${app.queue.topicExchange}", type = "topic"),
                key = ["\${app.queue.moodle.key}"]
            )
        ], containerFactory = "singlePrefetchConnectionFactory"
    )
    fun receiveMessage(message: MoodleJobMessage) {
        val jobEntry = mainJobLogic.getMainJobEntry(message.id)
        if (jobEntry == null || jobEntry.subJobs.isEmpty()) {
            log.error(if (jobEntry == null) "No job entry with id {}"
            else "Job entry with id {} has no sub jobs" , message.id)
            return
        }
        var subJob = jobEntry.subJobs.first()
        subJob.status = SubJobStatus.PROCESSING
        jobEntry.status = RenderingJobStatus.PROCESSING
        renderingJobRepository.save(jobEntry)
        subJob = subJobRepository.save(subJob)
        try {
            val url = moodleService.getUrl(
                moodleJobMessage = message,
                module = moduleRegistry.getRenderModule(jobEntry.module),
                repoId = jobEntry.repoId,
            )
            subJob.message = url.first
            subJob.additionalData = mapOf("linkUrl" to url.second)
            subJob.status = SubJobStatus.FINISHED
        } catch (exception: Exception) {
            log.error("Job id ${message.id} failed with exception: ${exception.message}", exception)
            subJob.status = SubJobStatus.FAILED
            if (exception is MoodleUploadException) {
                subJob.errorMessage = exception.publicMessage
            } else {
                subJob.errorMessage = GENERIC_CONVERSION_ERROR
            }
        }
        subJobRepository.save(subJob)
        mainJobLogic.processMainJob(message.id)
    }
}
