package org.edu_sharing.rendering.modules.sodix

import org.edu_sharing.rendering.core.annotation.ConditionalOnConverter
import org.edu_sharing.rendering.modules.ModuleRegistry
import org.edu_sharing.rendering.modules.moodle.MoodleReceiver
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
@ConditionalOnConverter
class SodixReceiver(
    private val sodixService: SodixApiCallerService,
    private val renderingJobRepository: RenderingJobRepository,
    private val subJobRepository: SubJobRepository,
    private val mainJobLogic: MainJobLogic,
    private val moduleRegistry: ModuleRegistry
) {
    private val log = LoggerFactory.getLogger(MoodleReceiver::class.java)

    @RabbitListener(
        bindings = [
            QueueBinding(
                value = Queue(name = "\${app.queue.sodix.name}", durable = "false"),
                exchange = Exchange(name = "\${app.queue.topicExchange}", type = "topic"),
                key = ["\${app.queue.sodix.key}"]
            )
        ], containerFactory = "singlePrefetchConnectionFactory"
    )
    fun receiveMessage(message: SodixJobMessage) {
        val jobEntry = mainJobLogic.getMainJobEntry(message.id)
        if (jobEntry == null || jobEntry.subJobs.isEmpty()) {
            log.error(if (jobEntry == null) "No job entry with id {}"
            else "Job entry with id {} has no sub jobs" , message.id)
            return
        }
        var subJob = jobEntry.subJobs.first()
        subJob.status = JobStatus.PROCESSING
        jobEntry.status = JobStatus.PROCESSING
        renderingJobRepository.save(jobEntry)
        subJob = subJobRepository.save(subJob)
        try {
            val url = sodixService.getContentUrl(
                sodixJobMessage = message,
                module = moduleRegistry.getRenderModule(jobEntry.module),
                repoId = jobEntry.repoId
            )
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