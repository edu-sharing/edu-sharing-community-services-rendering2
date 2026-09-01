package org.edu_sharing.rendering.renderingJob.queue

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.mockk
import org.edu_sharing.rendering.renderingJob.metrics.QueueConsumerMetrics
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.amqp.rabbit.config.DirectRabbitListenerContainerFactory
import org.springframework.amqp.rabbit.connection.AbstractConnectionFactory
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
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

    /**
     * `DirectMessageListenerContainer` invokes each consumer's listener synchronously on whichever
     * thread the RabbitMQ Java client's own `ConsumerWorkService` hands it — [rabbitConsumerExecutor]
     * never runs it. Left unconfigured, that service sizes itself from `Runtime.availableProcessors()`,
     * which rounds up to 1 on the cgroup-limited pods this app runs on, serializing every queue's
     * message handling regardless of `app.queue.<x>.concurrency`. This post-processor must hand
     * Spring's connection factory its own executor (via `AbstractConnectionFactory.setExecutor`, which
     * Spring actually passes into the RabbitMQ client — a `ConnectionFactoryCustomizer`/
     * `com.rabbitmq.client.ConnectionFactory#setSharedExecutor` would configure a field Spring's call
     * bypasses) so that ceiling doesn't apply.
     */
    @Test
    fun `connection factory post-processor gives Spring's connection factory a virtual-thread executor`() {
        val connectionFactory = CachingConnectionFactory()

        val result = config.rabbitConnectionFactoryExecutorPostProcessor()
            .postProcessBeforeInitialization(connectionFactory, "rabbitConnectionFactory")

        assertSame(connectionFactory, result) { "post-processor must return the same bean instance" }

        val executorField =
            AbstractConnectionFactory::class.java.getDeclaredField("executorService").apply { isAccessible = true }
        val executor = executorField.get(connectionFactory) as? ExecutorService
        assertNotNull(executor) { "post-processor must set the connection factory's executor" }

        val onVirtual = CompletableFuture<Boolean>()
        executor!!.execute { onVirtual.complete(Thread.currentThread().isVirtual) }
        assertTrue(onVirtual.get(5, TimeUnit.SECONDS)) { "executor must dispatch on virtual threads" }
    }

    @Test
    fun `connection factory post-processor leaves other beans untouched`() {
        val other = Any()
        val result = config.rabbitConnectionFactoryExecutorPostProcessor()
            .postProcessBeforeInitialization(other, "someOtherBean")
        assertSame(other, result)
    }

    @Test
    fun `queue listener factory is a DirectMessageListenerContainer factory`() {
        val factory = config.queueListenerContainerFactory(
            mockk<ConnectionFactory>(relaxed = true),
            JacksonJsonMessageConverter(),
            config.rabbitConsumerExecutor(),
            QueueConsumerMetrics(SimpleMeterRegistry()),
            QueueProperties().apply { prefetch = 1 },
        )
        assertInstanceOf(DirectRabbitListenerContainerFactory::class.java, factory)
    }
}
