package org.edu_sharing.rendering.config

import io.lettuce.core.RedisChannelHandler
import io.lettuce.core.event.command.CommandFailedEvent
import io.lettuce.core.event.command.CommandStartedEvent
import io.lettuce.core.event.command.CommandSucceededEvent
import io.lettuce.core.protocol.RedisCommand
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress

class RedisLettuceMetricsTest {

    private fun pending(registry: SimpleMeterRegistry) =
        registry.get("rendering.redis.commands.pending").gauge().value()

    private fun connections(registry: SimpleMeterRegistry) =
        registry.get("rendering.redis.connections.active").gauge().value()

    private fun disconnects(registry: SimpleMeterRegistry) =
        registry.get("rendering.redis.disconnects").counter().count()

    private fun command() = mockk<RedisCommand<Any, Any, Any>>(relaxed = true)

    private fun started() = CommandStartedEvent(command(), 0L)

    private fun succeeded() = CommandSucceededEvent(command(), emptyMap(), 0L, 1L)

    private fun failed() = CommandFailedEvent(command(), emptyMap(), RuntimeException("boom"))

    @Test
    fun `pending counts commands in flight and drains back to zero on success`() {
        val registry = SimpleMeterRegistry()
        val metrics = RedisLettuceMetrics(registry)

        assertEquals(0.0, pending(registry))

        metrics.commandStarted(started())
        metrics.commandStarted(started())
        // both commands are written but not yet answered – this is what grows while Redis is down
        assertEquals(2.0, pending(registry))

        metrics.commandSucceeded(succeeded())
        metrics.commandSucceeded(succeeded())
        assertEquals(0.0, pending(registry))
    }

    @Test
    fun `pending also drains when commands fail or are cancelled`() {
        val registry = SimpleMeterRegistry()
        val metrics = RedisLettuceMetrics(registry)

        repeat(3) { metrics.commandStarted(started()) }
        assertEquals(3.0, pending(registry))

        // Lettuce cancels buffered commands on reset()/close(); CommandWrapper.cancel() surfaces
        // that as commandFailed, so the gauge must not stay stuck high after an outage.
        repeat(3) { metrics.commandFailed(failed()) }
        assertEquals(0.0, pending(registry))
    }

    @Test
    fun `connection state gauge and disconnect counter track connect and disconnect`() {
        val registry = SimpleMeterRegistry()
        val metrics = RedisLettuceMetrics(registry)
        val connection = mockk<RedisChannelHandler<Any, Any>>(relaxed = true)

        assertEquals(0.0, connections(registry))
        assertEquals(0.0, disconnects(registry))

        metrics.onRedisConnected(connection, InetSocketAddress("localhost", 6379))
        assertEquals(1.0, connections(registry))
        assertEquals(0.0, disconnects(registry))

        metrics.onRedisDisconnected(connection)
        assertEquals(0.0, connections(registry))
        assertEquals(1.0, disconnects(registry))
    }

    @Test
    fun `connection errors are counted`() {
        val registry = SimpleMeterRegistry()
        val metrics = RedisLettuceMetrics(registry)
        val connection = mockk<RedisChannelHandler<Any, Any>>(relaxed = true)

        metrics.onRedisExceptionCaught(connection, RuntimeException("connection refused"))

        assertEquals(1.0, registry.get("rendering.redis.errors").counter().count())
    }
}
