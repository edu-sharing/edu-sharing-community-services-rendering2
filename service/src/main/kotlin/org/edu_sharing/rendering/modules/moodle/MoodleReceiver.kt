package org.edu_sharing.rendering.modules.moodle

import org.edu_sharing.rendering.config.annotation.ConditionalOnMoodle
import org.edu_sharing.rendering.modules.ModuleRegistry
import org.edu_sharing.rendering.renderingJob.MainJobLogic
import org.edu_sharing.rendering.renderingJob.entity.JobStatus
import org.edu_sharing.rendering.renderingJob.repository.RenderingJobRepository
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.Exchange
import org.springframework.amqp.rabbit.annotation.Queue
import org.springframework.amqp.rabbit.annotation.QueueBinding
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

@Component
@ConditionalOnMoodle
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
                value = Queue(name = "\${app.queue.moodle.name}", durable = "false"),
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
        val subJob = jobEntry.subJobs.first()
        subJob.status = JobStatus.PROCESSING
        jobEntry.status = JobStatus.PROCESSING
        renderingJobRepository.save(jobEntry)
        subJobRepository.save(subJob)
        try {
            val url = moodleService.getUrl(message, moduleRegistry.getRenderModule(jobEntry.module))
            subJob.status = JobStatus.FINISHED
            subJob.message = url
        } catch (exception: Exception) {
            subJob.status = JobStatus.FAILED
            subJob.message = exception.message
        }
        subJobRepository.save(subJob)
        mainJobLogic.processMainJob(message.id)
    }
}