package org.edu_sharing.rendering.queue

import org.springframework.stereotype.Component

@Component
class Receiver {
    fun receiveMessage(message: String) {
        println("Received: $message")
    }
}
