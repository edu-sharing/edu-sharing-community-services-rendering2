package org.edu_sharing.rendering.modules.binder

import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.modules.AbstractReceiver
import org.edu_sharing.rendering.renderingJob.MainJobLogic
import org.edu_sharing.rendering.renderingJob.queue.RenderingJobMessage
import org.springframework.amqp.rabbit.annotation.Exchange
import org.springframework.amqp.rabbit.annotation.Queue
import org.springframework.amqp.rabbit.annotation.QueueBinding
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

@Component
class BinderReceiver(
    val mainJobLogic: MainJobLogic,
    val mapper: Mapper,
    val binderUploadService: BinderUploadService
): AbstractReceiver(
    mainJobLogic = mainJobLogic,
    mapper = mapper,
    conversionService = binderUploadService
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
    fun receiveMessage(message: RenderingJobMessage) {
        super.processMessage(message, true)
    }
}