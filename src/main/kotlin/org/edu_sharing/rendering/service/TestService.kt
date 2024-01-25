package org.edu_sharing.rendering.service

import org.edu_sharing.rendering.dto.TestResponse
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.stereotype.Service

@Service
class TestService(
    private val rabbitTemplate: RabbitTemplate,
    ) {
    fun getMessage(): TestResponse {
        println("Sending message")
        rabbitTemplate.convertAndSend("rendering-exchange", "foo.bar.baz", "Hello from RabbitMQ!")
        return TestResponse("send", "queue")
    }
}