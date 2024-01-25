package org.edu_sharing.rendering.service

import org.edu_sharing.rendering.dto.TestResponse
import org.springframework.amqp.core.AmqpTemplate
import org.springframework.stereotype.Service

@Service
class TestService(
    private val amqpTemplate: AmqpTemplate,
    ) {
    fun getMessage(): TestResponse {
        println("Sending message")
        val testResponse = TestResponse("Received", "from queue")
        amqpTemplate.convertAndSend("rendering-exchange", "file", testResponse)
        return TestResponse("send", "to queue")
    }
}