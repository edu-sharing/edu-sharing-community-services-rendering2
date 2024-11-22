package org.edu_sharing.rendering.modules.document

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
class DocumentReceiver (
    val mainJobLogic: MainJobLogic,
    val mapper: Mapper,
    val documentConversionService: DocumentConversionService,
): AbstractReceiver(
    mainJobLogic = mainJobLogic,
    mapper = mapper,
    conversionService = documentConversionService
) {
    companion object {
        const val PUBLIC_FAILURE_MESSAGE = "Conversion failed"
    }

    @RabbitListener(
        bindings = [
            QueueBinding(
                value = Queue(name = "\${app.queue.document.name}", durable = "false"),
                exchange = Exchange(name = "\${app.queue.topicExchange}", type = "topic"),
                key = ["\${app.queue.document.key}"]
            )
        ], containerFactory = "singlePrefetchConnectionFactory"
    )
    fun receiveMessage(message: RenderingJobMessage) {
       super.processMessage(message, false)
    }
}