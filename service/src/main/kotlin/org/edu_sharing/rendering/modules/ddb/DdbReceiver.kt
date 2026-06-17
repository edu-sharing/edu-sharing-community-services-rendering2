package org.edu_sharing.rendering.modules.ddb

import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.renderingJob.MainJobLogic
import org.edu_sharing.rendering.renderingJob.entity.RenderingJobStatus
import org.edu_sharing.rendering.renderingJob.repository.RenderingJobRepository
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.Exchange
import org.springframework.amqp.rabbit.annotation.Queue
import org.springframework.amqp.rabbit.annotation.QueueBinding
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

@Component
class DdbReceiver(
    val mainJobLogic: MainJobLogic,
    val mapper: Mapper,
    val ddbApiService: DdbApiService,
    val renderingJobRepository: RenderingJobRepository
) {
    private val log = LoggerFactory.getLogger(this.javaClass)

    @RabbitListener(
        bindings = [
            QueueBinding(
                value = Queue(name = $$"${app.queue.ddb.name}", durable = "false"),
                exchange = Exchange(name = $$"${app.queue.topicExchange}", type = "topic"),
                key = [$$"${app.queue.ddb.key}"]
            )
        ], containerFactory = "singlePrefetchConnectionFactory"
    )
    fun receiveMessage(message: DdbJobMessage) {
        log.debug("Received DDB job message for jobId ${message.id}, remoteId ${message.remoteId}")
        val mainJob = mainJobLogic.getMainJobEntry(message.id)
        if (mainJob == null) {
            log.error("${this.javaClass.simpleName} received message with unknown job id ${message.id}")
            return
        }
        log.debug("Processing DDB job ${message.id}, nodeId ${mainJob.esObjectId}")
        renderingJobRepository.updateStatusWithoutVersion(mainJob.id, RenderingJobStatus.PROCESSING)
        ddbApiService.process(message.remoteId, mainJob)
        mainJobLogic.processMainJob(mainJob.id.toString())
    }
}
