package org.edu_sharing.rendering.modules.jupyter

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
class JupyterReceiver(
    val mainJobLogic: MainJobLogic,
    val mapper: Mapper,
    val jupyterConversionService: JupyterConversionService,
    val renderingJobRepository: RenderingJobRepository
): AbstractReceiver(
    mainJobLogic = mainJobLogic,
    mapper = mapper,
    conversionService = jupyterConversionService,
    renderingJobRepository = renderingJobRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @RabbitListener(
        bindings = [
            QueueBinding(
                value = Queue(name = "#{queueProperties.jupyter.name}", durable = "false"),
                exchange = Exchange(name = "#{queueProperties.topicExchange}", type = "topic"),
                key = ["#{queueProperties.jupyter.key}"]
            )
        ], containerFactory = "queueListenerContainerFactory",
        concurrency = "#{queueProperties.jupyter.effectiveConcurrency}"
    )
    fun receiveMessage(message: RenderingJobMessage) {
        log.debug("Jupyter message received: jobId={}", message.id)
        super.processMessage(message)
    }
}