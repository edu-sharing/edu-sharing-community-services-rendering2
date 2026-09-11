package org.edu_sharing.rendering.modules.binder

import org.bson.types.ObjectId
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

@Component
@ConditionalOnConverter
class BinderReceiver(
    private val uploadService: BinderUploadService,
    private val subJobRepository: SubJobRepository,
    private val jobRepository: RenderingJobRepository,
    private val mapper: Mapper
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @RabbitListener(
        bindings = [
            QueueBinding(
                value = Queue(name = "#{binderQueueProperties.name}", durable = "true"),
                exchange = Exchange(name = "#{queueProperties.topicExchange}", type = "topic"),
                key = ["#{binderQueueProperties.key}"]
            )
        ], containerFactory = "queueListenerContainerFactory",
        concurrency = "#{binderQueueProperties.effectiveConcurrency}"
    )
    fun receiveMessage(message: BinderSubJobMessage) {
        log.debug("Binder upload message received: subJobId={}", message.subJobId)
        val uploadSubJob = subJobRepository.findByIdOrNull(ObjectId(message.subJobId)) ?: return
        // RabbitMQ is at-least-once: guard against re-processing a redelivered message (e.g. the ack for
        // an already-finished upload was lost), which would re-run the git upload.
        if (uploadSubJob.status != SubJobStatus.QUEUED) {
            log.debug("Binder upload sub-job {} already past QUEUED ({}); dropping redelivered message", uploadSubJob.id, uploadSubJob.status)
            return
        }
        val mainJob = jobRepository.findByIdOrNull(uploadSubJob.parent.id) ?: return

        log.debug("Binder upload sub-job looked up: mainJobId={}, esObjectId={}", mainJob.id, mainJob.esObjectId)
        jobRepository.updateStatusWithoutVersion(mainJob.id, RenderingJobStatus.PROCESSING)

        uploadService.process(
            cacheObject = mapper.renderingJobToCacheObject(mainJob),
            uploadSubJob = uploadSubJob,
            module = mainJob.module
        )
    }
}