package org.edu_sharing.rendering.renderingJob.queue

import org.springframework.amqp.core.Message
import org.springframework.amqp.core.MessagePostProcessor

class PriorityPostProcessor(
        val priority: Int = 0
): MessagePostProcessor {
    override fun postProcessMessage(message: Message?): Message? {
        if (message != null) {
            message.messageProperties.priority = priority
        }
        return message
    }
}