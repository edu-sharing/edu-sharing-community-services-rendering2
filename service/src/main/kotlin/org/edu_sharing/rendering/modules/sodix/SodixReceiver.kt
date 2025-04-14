package org.edu_sharing.rendering.modules.sodix

import org.edu_sharing.rendering.core.ErrorStrings.GENERIC_CONVERSION_ERROR
import org.edu_sharing.rendering.core.annotation.ConditionalOnConverter
import org.edu_sharing.rendering.core.dto.ErrorMessage
import org.edu_sharing.rendering.modules.ModuleRegistry
import org.edu_sharing.rendering.modules.moodle.MoodleReceiver
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
import org.springframework.http.HttpStatus
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

        jobEntry.status = RenderingJobStatus.PROCESSING
        renderingJobRepository.save(jobEntry)
        var playoutUrlSubJob = jobEntry.subJobs.first { it.quality == 0}
        var downloadUrlSubJob = jobEntry.subJobs.firstOrNull { it.quality == 1}

        playoutUrlSubJob.status = SubJobStatus.PROCESSING
        playoutUrlSubJob = subJobRepository.save(playoutUrlSubJob)
        if (downloadUrlSubJob != null) {
            downloadUrlSubJob.status = SubJobStatus.PROCESSING
            downloadUrlSubJob = subJobRepository.save(downloadUrlSubJob)
        }

        try {
            val (playoutUrl, downloadUrl) = sodixService.getContentUrl(
                sodixJobMessage = message,
                module = moduleRegistry.getRenderModule(jobEntry.module),
                repoId = jobEntry.repoId,
                isPaidMedia = isPaidMedia
            )
            playoutUrlSubJob.status = SubJobStatus.FINISHED
            playoutUrlSubJob.message = playoutUrl
            subJobRepository.save(playoutUrlSubJob)
            if (downloadUrlSubJob != null) {
                downloadUrlSubJob.status = SubJobStatus.FINISHED
                downloadUrlSubJob.message = downloadUrl
                downloadUrlSubJob = subJobRepository.save(downloadUrlSubJob)
            }
        } catch (exception: Exception) {
            playoutUrlSubJob.status = SubJobStatus.FAILED
            playoutUrlSubJob.errorMessage = ErrorMessage(
                status = HttpStatus.INTERNAL_SERVER_ERROR.value(),
                message = exception.message,
                details = emptyMap(),
                exception = exception,
                userMessage = GENERIC_CONVERSION_ERROR
            )
            subJobRepository.save(playoutUrlSubJob)
            if (downloadUrlSubJob != null) {
                downloadUrlSubJob.status = SubJobStatus.FAILED
                downloadUrlSubJob.errorMessage = ErrorMessage(
                    status = HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    message = exception.message,
                    details = emptyMap(),
                    exception = exception,
                    userMessage = GENERIC_CONVERSION_ERROR
                )
                subJobRepository.save(downloadUrlSubJob)
            }
        }

        mainJobLogic.processMainJob(message.id)
    }
}