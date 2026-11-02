package org.edu_sharing.rendering.renderingJob.queue

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.bind.Bindable
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource

/**
 * Verifies the per-mode semantics encapsulated by [QueueSpec] and its three mode-fixing base classes. The
 * mode is fixed by which base class a queue extends (not by configuration), so there is no longer a runtime
 * whitelist to test — a queue simply cannot be the wrong mode. Each base is exercised through a minimal
 * concrete stand-in that mirrors what a module's `@ConfigurationProperties` bean looks like.
 */
class QueueSpecTest {

    private class TestStandard : StandardQueueProperties()
    private class TestRemote : RemoteQueueProperties()
    private class TestSingleActive : SingleActiveQueueProperties()

    @Test
    fun `STANDARD uses concurrency as the registered consumer count`() {
        assertEquals(4, TestStandard().apply { concurrency = 4 }.effectiveConcurrency)
    }

    @Test
    fun `REMOTE registers a single channel regardless of concurrency`() {
        assertEquals(1, TestRemote().apply { concurrency = 50 }.effectiveConcurrency)
    }

    @Test
    fun `SINGLE_ACTIVE is forced to one consumer`() {
        assertEquals(1, TestSingleActive().apply { concurrency = 8 }.effectiveConcurrency)
    }

    @Test
    fun `singleActiveConsumer is true only for SINGLE_ACTIVE`() {
        assertTrue(TestSingleActive().singleActiveConsumer)
        assertFalse(TestStandard().singleActiveConsumer)
        assertFalse(TestRemote().singleActiveConsumer)
    }

    @Test
    fun `remote prefetch defaults to concurrency and honours an explicit override`() {
        assertEquals(50, TestRemote().apply { concurrency = 50 }.effectiveRemotePrefetch)
        assertEquals(80, TestRemote().apply { concurrency = 50; prefetch = 80 }.effectiveRemotePrefetch)
    }

    @Test
    fun `validate rejects a remote prefetch below concurrency`() {
        val spec = TestRemote().apply { concurrency = 50; prefetch = 10 }
        assertThrows(IllegalArgumentException::class.java) { spec.validateRemotePrefetch() }
    }

    @Test
    fun `a queue is a quorum queue unless configured otherwise`() {
        assertEquals(QueueType.QUORUM, TestStandard().type)
        assertEquals("quorum", TestStandard().queueTypeArgument)
    }

    @Test
    fun `the type can be set per queue to classic`() {
        val spec = Binder(MapConfigurationPropertySource(mapOf("app.queue.x.type" to "classic")))
            .bind("app.queue.x", Bindable.ofInstance(TestStandard())).get()
        assertEquals(QueueType.CLASSIC, spec.type)
        assertEquals(mapOf<String, Any>("x-queue-type" to "classic"), spec.declareArguments)
    }

    @Test
    fun `declareArguments carries the queue type and never x-max-priority`() {
        // quorum queues reject x-max-priority (RabbitMQ 4.3: "invalid arg 'x-max-priority'"), priorities are per message
        assertEquals(mapOf<String, Any>("x-queue-type" to "quorum"), TestStandard().declareArguments)
        assertEquals(mapOf<String, Any>("x-queue-type" to "quorum"), TestRemote().declareArguments)
        assertFalse(TestStandard().declareArguments.containsKey("x-max-priority"))
    }

    @Test
    fun `a single-active queue declares the type and single-active-consumer`() {
        assertEquals(
            mapOf<String, Any>("x-queue-type" to "quorum", "x-single-active-consumer" to true),
            TestSingleActive().declareArguments,
        )
    }
}
