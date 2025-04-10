package org.edu_sharing.rendering.modules.binder

import org.bson.types.ObjectId
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.modules.binder.dto.BinderSubJobMessage
import org.edu_sharing.rendering.renderingJob.entity.JobStatus
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
                value = Queue(name = "\${app.queue.binderPreview.name}", durable = "false"),
                exchange = Exchange(name = "\${app.queue.topicExchange}", type = "topic"),
                key = ["\${app.queue.binderPreview.key}"]
            )
        ], containerFactory = "singlePrefetchConnectionFactory"
    )
    fun receiveMessage(message: BinderSubJobMessage) {
        var previewJob = subJobRepository.findByIdOrNull(ObjectId(message.subJobId)) ?: return
        var mainJob = jobRepository.findByIdOrNull(previewJob.parent.id) ?: return

        jobRepository.updateStatusWithoutVersion(mainJob.id, JobStatus.PROCESSING)

        previewJob.status = JobStatus.PROCESSING
        previewJob = subJobRepository.save(previewJob)

        val cacheObject = mapper.renderingJobToCacheObject(mainJob)
        try {
            binderPreviewService.process(cacheObject, mainJob.module)
            previewJob.status = JobStatus.FINISHED
        } catch (exception: Exception) {
            log.error("Binder preview job failed: ", exception)
            previewJob.status = JobStatus.FAILED
            previewJob.message = exception.message
        } finally {
            subJobRepository.save(previewJob)
            binderMainJobLogic.processMainJob(mainJob.id.toString())
        }
    }
}