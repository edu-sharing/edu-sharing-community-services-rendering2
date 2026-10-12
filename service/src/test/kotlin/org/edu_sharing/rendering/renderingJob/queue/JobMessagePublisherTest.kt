package org.edu_sharing.rendering.renderingJob.queue

import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.bson.types.ObjectId
import org.edu_sharing.rendering.core.ErrorStrings
import org.edu_sharing.rendering.renderingJob.entity.RenderingJobStatus
import org.edu_sharing.rendering.renderingJob.repository.RenderingJobRepository
import org.junit.jupiter.api.Test
import org.springframework.amqp.rabbit.connection.CorrelationData
import org.springframework.amqp.rabbit.core.RabbitTemplate

class JobMessagePublisherTest {
    private val rabbitTemplate: RabbitTemplate = mockk()
    private val jobRepository: RenderingJobRepository = mockk()
    private val underTest = JobMessagePublisher(rabbitTemplate, jobRepository)
    private val jobId = ObjectId("507f191e810c19729de860eb")

    private fun expectFailJob() {
        justRun { jobRepository.updateErrorMessageWithoutVersion(jobId, ErrorStrings.ERROR_PROCESSING_JOB) }
        justRun { jobRepository.updateStatusWithoutVersion(jobId, RenderingJobStatus.FAILED) }
    }

    @Test
    fun `successful publish leaves the job untouched and correlates the message with the job id`() {
        val correlation = slot<CorrelationData>()
        justRun { rabbitTemplate.convertAndSend("ex", "key", "msg", capture(correlation)) }

        underTest.publishForJob("ex", "key", "msg", jobId)

        assert(correlation.captured.id == jobId.toString())
        verify(exactly = 0) { jobRepository.updateStatusWithoutVersion(any(), any()) }
    }

    @Test
    fun `publish exception fails the job and is rethrown`() {
        every { rabbitTemplate.convertAndSend("ex", "key", "msg", any<CorrelationData>()) } throws RuntimeException("broker down")
        expectFailJob()

        val thrown = runCatching { underTest.publishForJob("ex", "key", "msg", jobId) }.exceptionOrNull()

        assert(thrown?.message == "broker down")
        verify(exactly = 1) { jobRepository.updateStatusWithoutVersion(jobId, RenderingJobStatus.FAILED) }
        verify(exactly = 1) { jobRepository.updateErrorMessageWithoutVersion(jobId, ErrorStrings.ERROR_PROCESSING_JOB) }
    }

    @Test
    fun `a broker nack reported through the correlation data fails the job`() {
        val correlation = slot<CorrelationData>()
        justRun { rabbitTemplate.convertAndSend("ex", "key", "msg", capture(correlation)) }
        expectFailJob()

        underTest.publishForJob("ex", "key", "msg", jobId)
        // This is what QueueConfig's confirm callback does for a nack.
        (correlation.captured as JobCorrelationData).unconfirmed("broker nack: out of memory")

        verify(exactly = 1) { jobRepository.updateStatusWithoutVersion(jobId, RenderingJobStatus.FAILED) }
    }

    @Test
    fun `a failing compensation does not throw out of the confirm callback`() {
        val correlation = slot<CorrelationData>()
        justRun { rabbitTemplate.convertAndSend("ex", "key", "msg", capture(correlation)) }
        every { jobRepository.updateErrorMessageWithoutVersion(any(), any()) } throws RuntimeException("mongo down")

        underTest.publishForJob("ex", "key", "msg", jobId)
        (correlation.captured as JobCorrelationData).unconfirmed("nack")
    }
}
