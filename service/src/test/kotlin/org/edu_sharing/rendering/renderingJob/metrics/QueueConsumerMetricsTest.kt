package org.edu_sharing.rendering.renderingJob.metrics

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import org.aopalliance.intercept.MethodInvocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.amqp.core.Message
import org.springframework.amqp.core.MessageProperties

class QueueConsumerMetricsTest {

    private fun gauge(registry: SimpleMeterRegistry, queue: String) =
        registry.get("rendering.queue.consumers.active").tag("queue", queue).gauge().value()

    private fun message(queue: String) =
        Message("x".toByteArray(), MessageProperties().apply { setConsumerQueue(queue) })

    @Test
    fun `counts one active consumer while processing and zero after completion`() {
        val registry = SimpleMeterRegistry()
        val metrics = QueueConsumerMetrics(registry)

        val invocation = mockk<MethodInvocation>()
        every { invocation.arguments } returns arrayOf(message("image_job_queue"))
        every { invocation.proceed() } answers {
            // while the listener runs, the queue's gauge must read 1
            assertEquals(1.0, gauge(registry, "image_job_queue"))
            "done"
        }

        assertEquals("done", metrics.invoke(invocation))
        // decremented back to 0 in the finally block
        assertEquals(0.0, gauge(registry, "image_job_queue"))
    }

    @Test
    fun `decrements even when the listener throws`() {
        val registry = SimpleMeterRegistry()
        val metrics = QueueConsumerMetrics(registry)

        val invocation = mockk<MethodInvocation>()
        every { invocation.arguments } returns arrayOf(message("sodix_job_queue"))
        every { invocation.proceed() } throws RuntimeException("boom")

        runCatching { metrics.invoke(invocation) }
        assertEquals(0.0, gauge(registry, "sodix_job_queue"))
    }
}
