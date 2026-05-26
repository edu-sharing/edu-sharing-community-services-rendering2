package org.edu_sharing.rendering.edusharingRepo.cors

import org.edu_sharing.rendering.core.annotation.ConditionalOnMasterOrController
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
    @RabbitListener(
        bindings = [
            QueueBinding(
                value = Queue(
                    name = "",
                    durable = "true",
                    autoDelete = "true"
                ),
                exchange = Exchange(
                    name = "\${app.queue.controllerBroadcastExchange}",
                    type = "fanout"
                )
            )
        ],
        containerFactory = "singlePrefetchConnectionFactory"
    )
    fun handleBroadcast(message: String) {
       corsSyncService.applyKnownOrigins()
    }
}
