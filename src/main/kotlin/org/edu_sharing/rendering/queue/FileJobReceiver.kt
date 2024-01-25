package org.edu_sharing.rendering.queue

import org.edu_sharing.rendering.dto.TestResponse
import org.springframework.stereotype.Component

@Component
class FileJobReceiver {
    fun receiveMessage(testResponse: TestResponse) {
        println("received message")
        println(testResponse.minioMessage)
        println(testResponse.redisMessage)
    }
}
