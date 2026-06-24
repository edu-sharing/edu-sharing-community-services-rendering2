package org.edu_sharing.rendering.modules.document

import org.edu_sharing.rendering.core.annotation.ConditionalOnConverter
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.modules.AbstractReceiver
import org.edu_sharing.rendering.renderingJob.MainJobLogic
import org.edu_sharing.rendering.renderingJob.queue.RenderingJobMessage
import org.edu_sharing.rendering.renderingJob.repository.RenderingJobRepository
import org.slf4j.LoggerFactory
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
    val renderingJobRepository: RenderingJobRepository
): AbstractReceiver(
    mainJobLogic = mainJobLogic,
    mapper = mapper,
    conversionService = documentConversionService,
    renderingJobRepository = renderingJobRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        const val PUBLIC_FAILURE_MESSAGE = "Conversion failed"
    }

    @RabbitListener(
        bindings = [
            QueueBinding(
                value = Queue(name = $$"${app.queue.document.name}", durable = "false"),
                exchange = Exchange(name = $$"${app.queue.topicExchange}", type = "topic"),
                key = [$$"${app.queue.document.key}"]
            )
        ], containerFactory = "singlePrefetchConnectionFactory"
    )
    fun receiveMessage(message: RenderingJobMessage) {
        log.debug("Received document conversion message: id=${message.id}")
        super.processMessage(message)
    }
}