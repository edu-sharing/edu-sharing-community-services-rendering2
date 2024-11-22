package org.edu_sharing.rendering.modules.jupyter

import org.edu_sharing.rendering.core.annotation.ConditionalOnConverter
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.modules.AbstractReceiver
import org.edu_sharing.rendering.renderingJob.MainJobLogic
import org.edu_sharing.rendering.renderingJob.queue.RenderingJobMessage
import org.springframework.amqp.rabbit.annotation.Exchange
import org.springframework.amqp.rabbit.annotation.Queue
import org.springframework.amqp.rabbit.annotation.QueueBinding
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

@ConditionalOnConverter
@Component
class JupyterReceiver(
    val mainJobLogic: MainJobLogic,
    val mapper: Mapper,
    val jupyterConversionService: JupyterConversionService
): AbstractReceiver(
    mainJobLogic = mainJobLogic,
    mapper = mapper,
    conversionService = jupyterConversionService
) {
    @RabbitListener(
        bindings = [
            QueueBinding(
                value = Queue(name = "\${app.queue.jupyter.name}", durable = "false"),
                exchange = Exchange(name = "\${app.queue.topicExchange}", type = "topic"),
                key = ["\${app.queue.jupyter.key}"]
            )
        ], containerFactory = "singlePrefetchConnectionFactory"
    )
    fun receiveMessage(message: RenderingJobMessage) {
        super.processMessage(message, false)
    }
}