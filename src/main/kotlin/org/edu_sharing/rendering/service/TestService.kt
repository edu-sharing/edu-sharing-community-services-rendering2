package org.edu_sharing.rendering.service

import org.edu_sharing.rendering.dto.TestResponse
import org.edu_sharing.rendering.entity.RenderingJob
import org.edu_sharing.rendering.repository.mongo.RenderingJobRepository
import org.springframework.amqp.core.AmqpTemplate
import org.springframework.stereotype.Service

@Service
class TestService(
    private val amqpTemplate: AmqpTemplate,
    private val mongoRepo: RenderingJobRepository
    ) {
    fun getMessage(): TestResponse {
        println("storing in mongo")
        val myJob = RenderingJob(
            status = "running",
            subJobs = listOf("job1", "job2"),
            type = "copy"
        )
        mongoRepo.save(myJob)
        println("Sending message")
        val testResponse = TestResponse("Received", "from queue")
        amqpTemplate.convertAndSend("rendering-exchange", "file", testResponse)
        return TestResponse("send", "to queue")
    }
}