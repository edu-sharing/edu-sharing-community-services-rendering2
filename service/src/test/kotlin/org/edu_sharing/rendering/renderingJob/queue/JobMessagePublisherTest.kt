package org.edu_sharing.rendering.renderingJob.queue

import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.bson.types.ObjectId
import org.edu_sharing.rendering.core.ErrorStrings
import org.edu_sharing.rendering.renderingJob.repository.RenderingJobRepository
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.junit.jupiter.api.Test
import org.springframework.amqp.core.MessagePostProcessor
import org.springframework.amqp.rabbit.connection.CorrelationData
import org.springframework.amqp.rabbit.core.RabbitTemplate

class JobMessagePublisherTest {
    private val rabbitTemplate: RabbitTemplate = mockk()
    private val jobRepository: RenderingJobRepository = mockk()
    private val subJobRepository: SubJobRepository = mockk()
    private val underTest = JobMessagePublisher(rabbitTemplate, jobRepository, subJobRepository)
    private val jobId = ObjectId("507f191e810c19729de860eb")
    private val subJobA = ObjectId("507f191e810c19729de860ec")
    private val subJobB = ObjectId("507f191e810c19729de860ed")
    private val resolved = mutableListOf<String>()

    private fun expectFailJob(changed: Boolean = true) {
        every { jobRepository.failIfActive(jobId, ErrorStrings.ERROR_PROCESSING_JOB) } returns changed
    }

    @Test
    fun `successful publish leaves the job untouched and correlates the message with the job id`() {
        val correlation = slot<CorrelationData>()
        justRun { rabbitTemplate.convertAndSend("ex", "key", "msg", capture(correlation)) }

        underTest.publishForJob("ex", "key", "msg", jobId)

        assert(correlation.captured.id == jobId.toString())
        verify(exactly = 0) { jobRepository.failIfActive(any(), any()) }
    }

    @Test
    fun `publish exception fails the job and is rethrown`() {
        every { rabbitTemplate.convertAndSend("ex", "key", "msg", any<CorrelationData>()) } throws RuntimeException("broker down")
        expectFailJob()

        val thrown = runCatching { underTest.publishForJob("ex", "key", "msg", jobId) }.exceptionOrNull()

        assert(thrown?.message == "broker down")
        verify(exactly = 1) { jobRepository.failIfActive(jobId, ErrorStrings.ERROR_PROCESSING_JOB) }
    }

    @Test
    fun `a broker nack reported through the correlation data fails the job`() {
        val correlation = slot<CorrelationData>()
        justRun { rabbitTemplate.convertAndSend("ex", "key", "msg", capture(correlation)) }
        expectFailJob()

        underTest.publishForJob("ex", "key", "msg", jobId)
        // This is what QueueConfig's confirm callback does for a nack.
        (correlation.captured as JobCorrelationData).unconfirmed("broker nack: out of memory")

        verify(exactly = 1) { jobRepository.failIfActive(jobId, ErrorStrings.ERROR_PROCESSING_JOB) }
    }

    @Test
    fun `a failing compensation does not throw out of the confirm callback`() {
        val correlation = slot<CorrelationData>()
        justRun { rabbitTemplate.convertAndSend("ex", "key", "msg", capture(correlation)) }
        every { jobRepository.failIfActive(any(), any()) } throws RuntimeException("mongo down")

        underTest.publishForJob("ex", "key", "msg", jobId)
        (correlation.captured as JobCorrelationData).unconfirmed("nack")
    }

    @Test
    fun `a terminal job is not overwritten by a late nack`() {
        val correlation = slot<CorrelationData>()
        justRun { rabbitTemplate.convertAndSend("ex", "key", "msg", capture(correlation)) }
        expectFailJob(changed = false)

        underTest.publishForJob("ex", "key", "msg", jobId)
        (correlation.captured as JobCorrelationData).unconfirmed("nack")

        // The atomic update only touches QUEUED/PROCESSING jobs; nothing else may write the job.
        verify(exactly = 1) { jobRepository.failIfActive(jobId, ErrorStrings.ERROR_PROCESSING_JOB) }
        verify(exactly = 0) { jobRepository.updateStatusWithoutVersion(any(), any()) }
    }

    private fun expectFailSubJobs(a: Boolean = true, b: Boolean = true) {
        every { subJobRepository.failIfQueued(subJobA, ErrorStrings.GENERIC_CONVERSION_ERROR) } returns a
        every { subJobRepository.failIfQueued(subJobB, ErrorStrings.GENERIC_CONVERSION_ERROR) } returns b
    }

    @Test
    fun `sub-job publish success leaves everything untouched`() {
        justRun { rabbitTemplate.convertAndSend("ex", "key", "msg", any<CorrelationData>()) }

        underTest.publishForSubJobs("ex", "key", "msg", jobId, listOf(subJobA), { resolved += it })

        verify(exactly = 0) { subJobRepository.failIfQueued(any(), any()) }
        assert(resolved.isEmpty())
    }

    @Test
    fun `sub-job publish exception fails the sub-jobs, resolves the main job and is rethrown`() {
        every { rabbitTemplate.convertAndSend("ex", "key", "msg", any<CorrelationData>()) } throws RuntimeException("broker down")
        expectFailSubJobs()

        val thrown = runCatching {
            underTest.publishForSubJobs("ex", "key", "msg", jobId, listOf(subJobA, subJobB), { resolved += it })
        }.exceptionOrNull()

        assert(thrown?.message == "broker down")
        verify(exactly = 1) { subJobRepository.failIfQueued(subJobA, ErrorStrings.GENERIC_CONVERSION_ERROR) }
        verify(exactly = 1) { subJobRepository.failIfQueued(subJobB, ErrorStrings.GENERIC_CONVERSION_ERROR) }
        assert(resolved == listOf(jobId.toString()))
        // the whole main job is never failed directly
        verify(exactly = 0) { jobRepository.failIfActive(any(), any()) }
    }

    @Test
    fun `sub-job publish with a post processor uses the post-processor overload`() {
        val processor = MessagePostProcessor { it }
        every { rabbitTemplate.convertAndSend("ex", "key", "msg", processor, any<CorrelationData>()) } throws RuntimeException("down")
        expectFailSubJobs()

        runCatching { underTest.publishForSubJobs("ex", "key", "msg", jobId, listOf(subJobA), { resolved += it }, processor) }

        verify(exactly = 1) { rabbitTemplate.convertAndSend("ex", "key", "msg", processor, any<CorrelationData>()) }
        assert(resolved == listOf(jobId.toString()))
    }

    @Test
    fun `a nack for sub-jobs fails them and resolves the main job`() {
        val correlation = slot<CorrelationData>()
        justRun { rabbitTemplate.convertAndSend("ex", "key", "msg", capture(correlation)) }
        expectFailSubJobs()

        underTest.publishForSubJobs("ex", "key", "msg", jobId, listOf(subJobA, subJobB), { resolved += it })
        (correlation.captured as JobCorrelationData).unconfirmed("unroutable: NO_ROUTE")

        verify(exactly = 1) { subJobRepository.failIfQueued(subJobA, ErrorStrings.GENERIC_CONVERSION_ERROR) }
        assert(resolved == listOf(jobId.toString()))
    }

    @Test
    fun `a sub-job already taken by a consumer is not failed and the main job is not resolved`() {
        val correlation = slot<CorrelationData>()
        justRun { rabbitTemplate.convertAndSend("ex", "key", "msg", capture(correlation)) }
        expectFailSubJobs(a = false, b = false)

        underTest.publishForSubJobs("ex", "key", "msg", jobId, listOf(subJobA, subJobB), { resolved += it })
        (correlation.captured as JobCorrelationData).unconfirmed("nack")

        assert(resolved.isEmpty())
    }

    @Test
    fun `a failing sub-job compensation does not throw out of the confirm callback`() {
        val correlation = slot<CorrelationData>()
        justRun { rabbitTemplate.convertAndSend("ex", "key", "msg", capture(correlation)) }
        every { subJobRepository.failIfQueued(any(), any()) } throws RuntimeException("mongo down")

        underTest.publishForSubJobs("ex", "key", "msg", jobId, listOf(subJobA), { resolved += it })
        (correlation.captured as JobCorrelationData).unconfirmed("nack")

        assert(resolved.isEmpty())
    }
}
