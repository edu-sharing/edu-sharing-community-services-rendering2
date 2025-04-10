package org.edu_sharing.rendering.modules.binder

import org.bson.types.ObjectId
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.modules.binder.dto.BinderSubJobMessage
import org.edu_sharing.rendering.renderingJob.entity.JobStatus
import org.edu_sharing.rendering.renderingJob.repository.RenderingJobRepository
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.springframework.amqp.rabbit.annotation.Exchange
import org.springframework.amqp.rabbit.annotation.Queue
import org.springframework.amqp.rabbit.annotation.QueueBinding
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class BinderReceiver(
    private val uploadService: BinderUploadService,
    private val subJobRepository: SubJobRepository,
    private val jobRepository: RenderingJobRepository,
    private val mapper: Mapper
) {
    @RabbitListener(
        bindings = [
            QueueBinding(
                value = Queue(name = "\${app.queue.binder.name}", durable = "false"),
                exchange = Exchange(name = "\${app.queue.topicExchange}", type = "topic"),
                key = ["\${app.queue.binder.key}"]
            )
        ], containerFactory = "singlePrefetchConnectionFactory"
    )
    fun receiveMessage(message: BinderSubJobMessage) {
        var uploadSubJob = subJobRepository.findByIdOrNull(ObjectId(message.subJobId)) ?: return
        var mainJob = jobRepository.findByIdOrNull(uploadSubJob.parent.id) ?: return

        jobRepository.updateStatusWithoutVersion(mainJob.id, JobStatus.PROCESSING)

        uploadService.process(
            cacheObject = mapper.renderingJobToCacheObject(mainJob),
            uploadSubJob = uploadSubJob,
            module = mainJob.module
        )
    }
}