package org.edu_sharing.rendering.renderingJob.queue

import com.rabbitmq.client.Channel
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.edu_sharing.rendering.renderingJob.metrics.QueueConsumerMetrics
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.core.task.SimpleAsyncTaskExecutor
import java.lang.ref.WeakReference
import java.lang.reflect.Proxy
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class AsyncAckDispatcherTest {

    private val dispatcher = AsyncAckDispatcher(
        SimpleAsyncTaskExecutor().apply { setVirtualThreads(true) },
        QueueConsumerMetrics(SimpleMeterRegistry()),
    )

    /** A plain JDK proxy (no mocking framework keeps a reference to it), counting overlapping (n)acks. */
    private class Probe {
        val acks = AtomicInteger()
        val concurrent = AtomicInteger()
        val maxConcurrent = AtomicInteger()
    }

    private fun channel(probe: Probe, slow: Boolean = false): Channel =
        Proxy.newProxyInstance(javaClass.classLoader, arrayOf(Channel::class.java)) { proxy, method, args ->
            // identity semantics for the Object methods, as a real channel has
            when (method.name) {
                "hashCode" -> return@newProxyInstance System.identityHashCode(proxy)
                "equals" -> return@newProxyInstance proxy === args[0]
                "toString" -> return@newProxyInstance "probe-channel"
            }
            if (method.name == "basicAck" || method.name == "basicNack") {
                val now = probe.concurrent.incrementAndGet()
                probe.maxConcurrent.accumulateAndGet(now, ::maxOf)
                if (slow) Thread.sleep(2)
                probe.concurrent.decrementAndGet()
                probe.acks.incrementAndGet()
            }
            null
        } as Channel

    @Test
    fun parallelAcksOnTheSameChannelAreSerialized() {
        val probe = Probe()
        val channel = channel(probe, slow = true)
        val n = 40

        repeat(n) { tag -> dispatcher.dispatch(channel, tag.toLong(), "q") { } }
        // dispatch returns at once; wait until every ack was issued
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
        while (probe.acks.get() < n && System.nanoTime() < deadline) Thread.sleep(10)

        assertEquals(n, probe.acks.get())
        assertEquals(1, probe.maxConcurrent.get(), "acks on one channel must never overlap")
    }

    @Test
    fun aDroppedChannelLeavesNoPermanentEntry() {
        val probe = Probe()
        var channel: Channel? = channel(probe)
        dispatcher.dispatch(channel!!, 1, "q") { }
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (probe.acks.get() < 1 && System.nanoTime() < deadline) Thread.sleep(10)
        assertEquals(1, dispatcher.trackedChannelCount())

        val reference = WeakReference(channel)
        channel = null
        val gcDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (dispatcher.trackedChannelCount() > 0 && System.nanoTime() < gcDeadline) {
            System.gc()
            Thread.sleep(20)
        }

        assertEquals(0, dispatcher.trackedChannelCount())
        assertTrue(reference.get() == null)
    }
}
