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
        val isPaidMedia = jobEntry.subJobs.size == 2

        jobEntry.status = JobStatus.PROCESSING
        renderingJobRepository.save(jobEntry)
        var playoutUrlSubJob = jobEntry.subJobs.first { it.quality == 0}
        var downloadUrlSubJob = jobEntry.subJobs.firstOrNull { it.quality == 1}

        playoutUrlSubJob.status = JobStatus.PROCESSING
        playoutUrlSubJob = subJobRepository.save(playoutUrlSubJob)
        if (downloadUrlSubJob != null) {
            downloadUrlSubJob.status = JobStatus.PROCESSING
            downloadUrlSubJob = subJobRepository.save(downloadUrlSubJob)
        }

        try {
            val (playoutUrl, downloadUrl) = sodixService.getContentUrl(
                sodixJobMessage = message,
                module = moduleRegistry.getRenderModule(jobEntry.module),
                repoId = jobEntry.repoId,
                isPaidMedia = isPaidMedia
            )
            playoutUrlSubJob.status = JobStatus.FINISHED
            playoutUrlSubJob.message = playoutUrl
            subJobRepository.save(playoutUrlSubJob)
            if (downloadUrlSubJob != null) {
                downloadUrlSubJob.status = JobStatus.FINISHED
                downloadUrlSubJob.message = downloadUrl
                downloadUrlSubJob = subJobRepository.save(downloadUrlSubJob)
            }
        } catch (exception: Exception) {
            playoutUrlSubJob.status = JobStatus.FAILED
            playoutUrlSubJob.message = exception.message
            subJobRepository.save(playoutUrlSubJob)
            if (downloadUrlSubJob != null) {
                downloadUrlSubJob.status = JobStatus.FAILED
                downloadUrlSubJob.message = exception.message
                subJobRepository.save(downloadUrlSubJob)
            }
        }

        mainJobLogic.processMainJob(message.id)
    }
}