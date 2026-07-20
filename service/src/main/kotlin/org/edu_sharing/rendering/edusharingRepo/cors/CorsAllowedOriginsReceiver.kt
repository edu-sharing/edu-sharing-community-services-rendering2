package org.edu_sharing.rendering.edusharingRepo.cors

import org.edu_sharing.rendering.core.annotation.ConditionalOnMasterOrController
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.Exchange
import org.springframework.amqp.rabbit.annotation.Queue
import org.springframework.amqp.rabbit.annotation.QueueBinding
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

@ConditionalOnMasterOrController
@Component
class CorsAllowedOriginsReceiver(
    private val corsSyncService: CorsSyncService
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @RabbitListener(
        bindings = [
            QueueBinding(
                value = Queue(
                    name = "",
                    durable = "true",
                    autoDelete = "true"
                ),
                exchange = Exchange(
                    name = "#{queueProperties.controllerBroadcastExchange}",
                    type = "fanout"
                )
            )
        ],
        containerFactory = "queueListenerContainerFactory"
    )
    fun handleBroadcast(message: String) {
        log.debug("Received CORS broadcast message; applying known origins")
        corsSyncService.applyKnownOrigins()
    }
}
