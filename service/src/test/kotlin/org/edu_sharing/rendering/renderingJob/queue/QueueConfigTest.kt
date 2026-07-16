package org.edu_sharing.rendering.renderingJob.queue

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.mockk
import org.edu_sharing.rendering.renderingJob.metrics.QueueConsumerMetrics
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.amqp.rabbit.config.DirectRabbitListenerContainerFactory
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * Verifies the queue-listener infrastructure: consumers run on virtual threads and the shared
 * factory produces [org.springframework.amqp.rabbit.listener.DirectMessageListenerContainer]s
 * (no `SimpleMessageListenerContainer` auto-scale). Full per-role bean wiring is exercised by the
 * integration role tests.
 */
class QueueConfigTest {

    private val config = QueueConfig()

    @Test
    fun `consumer executor runs tasks on virtual threads`() {
        val onVirtual = CompletableFuture<Boolean>()
        config.rabbitConsumerExecutor().execute { onVirtual.complete(Thread.currentThread().isVirtual) }
        assertTrue(onVirtual.get(5, TimeUnit.SECONDS)) { "rabbit consumer tasks must run on virtual threads" }
    }

    @Test
    fun `queue listener factory is a DirectMessageListenerContainer factory`() {
        val factory = config.queueListenerContainerFactory(
            mockk<ConnectionFactory>(relaxed = true),
            JacksonJsonMessageConverter(),
            config.rabbitConsumerExecutor(),
            QueueConsumerMetrics(SimpleMeterRegistry()),
            1,
        )
        assertInstanceOf(DirectRabbitListenerContainerFactory::class.java, factory)
    }
}
