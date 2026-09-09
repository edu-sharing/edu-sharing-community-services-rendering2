package org.edu_sharing.rendering.modules.binder

import org.bson.types.ObjectId
import org.edu_sharing.rendering.core.ErrorStrings.GENERIC_CONVERSION_ERROR
import org.edu_sharing.rendering.core.annotation.ConditionalOnConverter
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.modules.binder.dto.BinderSubJobMessage
import org.edu_sharing.rendering.renderingJob.entity.RenderingJobStatus
import org.edu_sharing.rendering.renderingJob.entity.SubJobStatus
import org.edu_sharing.rendering.renderingJob.repository.RenderingJobRepository
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.Exchange
import org.springframework.amqp.rabbit.annotation.Queue
import org.springframework.amqp.rabbit.annotation.QueueBinding
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import java.time.Instant

@Component
@ConditionalOnConverter
class BinderPreviewReceiver(
    private val binderPreviewService: BinderPreviewService,
    private val jobRepository: RenderingJobRepository,
    private val subJobRepository: SubJobRepository,
    private val mapper: Mapper,
    private val binderMainJobLogic: BinderMainJobLogic
) {

    private val log = LoggerFactory.getLogger(BinderPreviewReceiver::class.java)

    @RabbitListener(
        bindings = [
            QueueBinding(
                value = Queue(name = "#{binderPreviewQueueProperties.name}", durable = "false"),
                exchange = Exchange(name = "#{queueProperties.topicExchange}", type = "topic"),
                key = ["#{binderPreviewQueueProperties.key}"]
            )
        ], containerFactory = "queueListenerContainerFactory",
        concurrency = "#{binderPreviewQueueProperties.effectiveConcurrency}"
    )
    fun receiveMessage(message: BinderSubJobMessage) {
        log.debug("Binder preview message received: subJobId={}", message.subJobId)
        var previewJob = subJobRepository.findByIdOrNull(ObjectId(message.subJobId)) ?: return
        // RabbitMQ is at-least-once: guard against re-processing a redelivered message (e.g. the ack for
        // an already-finished preview was lost), which would re-run the preview build.
        if (previewJob.status != SubJobStatus.QUEUED) {
            log.debug("Binder preview sub-job {} already past QUEUED ({}); dropping redelivered message", previewJob.id, previewJob.status)
            return
        }
        val mainJob = jobRepository.findByIdOrNull(previewJob.parent.id) ?: return

        jobRepository.updateStatusWithoutVersion(mainJob.id, RenderingJobStatus.PROCESSING)

        previewJob.status = SubJobStatus.PROCESSING
        previewJob.processingStartedDate = Instant.now()
        previewJob = subJobRepository.save(previewJob)

        val cacheObject = mapper.renderingJobToCacheObject(mainJob)
        log.debug("Binder preview processing for nodeId={}, externalUrl={}", cacheObject.nodeId, cacheObject.externalUrl)
        try {
            binderPreviewService.process(cacheObject, mainJob.module)
            previewJob.status = SubJobStatus.FINISHED
        } catch (exception: Exception) {
            log.error("Binder preview job failed: ", exception)
            previewJob.status = SubJobStatus.FAILED
            previewJob.errorMessage = GENERIC_CONVERSION_ERROR
        } finally {
            previewJob.finishedDate = Instant.now()
            subJobRepository.save(previewJob)
            binderMainJobLogic.processMainJob(mainJob.id.toString())
        }
    }
}