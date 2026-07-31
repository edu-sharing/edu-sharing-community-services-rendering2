package org.edu_sharing.rendering.utils

import io.netty.buffer.PooledByteBufAllocator
import org.junit.jupiter.api.Test
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import org.springframework.core.io.buffer.NettyDataBufferFactory
import org.springframework.core.io.buffer.PooledDataBuffer
import reactor.core.publisher.Flux
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

class FluxInputStreamTest {

    private val bufferFactory = DefaultDataBufferFactory()

    /**
     * Reference-counted factory — [DefaultDataBufferFactory] buffers are not pooled, so the tests
     * above cannot observe a missing release. The production source is reactor-netty, whose buffers
     * are pooled and direct; leaking one pins a 2 MiB allocator chunk off-heap.
     */
    private val pooledFactory = NettyDataBufferFactory(PooledByteBufAllocator(true))

    private fun buffer(text: String): DataBuffer =
        bufferFactory.wrap(text.toByteArray(StandardCharsets.UTF_8))

    private fun pooledBuffer(text: String): DataBuffer {
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        return pooledFactory.allocateBuffer(bytes.size).write(bytes)
    }

    private fun awaitReleased(buffers: List<DataBuffer>, what: String) {
        val deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos()
        while (System.nanoTime() < deadline && buffers.any { (it as PooledDataBuffer).isAllocated }) {
            Thread.sleep(10)
        }
        val stillAllocated = buffers.count { (it as PooledDataBuffer).isAllocated }
        assert(stillAllocated == 0) { "$stillAllocated of ${buffers.size} $what were never released" }
    }

    @Test
    fun testStreamsAllDataBuffersInOrder() {
        val flux = Flux.just(buffer("hello "), buffer("streaming "), buffer("world"))

        val text = FluxInputStream.toInputStream(flux).use { it.bufferedReader().readText() }

        assert(text == "hello streaming world")
    }

    @Test
    fun testClosingTheStreamDisposesTheUpstreamSubscription() {
        val cancelled = AtomicBoolean(false)
        // A never-completing source so the only way the subscription ends is via cancellation.
        val flux = Flux.concat(
            Flux.just(buffer("partial")),
            Flux.never<DataBuffer>()
        ).doOnCancel { cancelled.set(true) }

        val stream = FluxInputStream.toInputStream(flux)
        // Read the first bytes, then abandon the stream (simulating a client disconnect).
        val first = stream.readNBytes(7)
        assert(String(first, StandardCharsets.UTF_8) == "partial")

        stream.close()

        // close() must dispose the subscription so the upstream Flux is cancelled and its
        // (pooled, off-heap) DataBuffers are released instead of leaking until GC.
        val deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos()
        while (!cancelled.get() && System.nanoTime() < deadline) {
            Thread.sleep(10)
        }
        assert(cancelled.get()) { "Closing the InputStream should cancel the upstream Flux subscription" }
    }

    /**
     * The regression test for the production leak: `DataBufferUtils.write(source, OutputStream)`
     * emits its buffers **unreleased**, so subscribing without `releaseConsumer()` leaks every
     * buffer of every successful transfer — no error needed.
     */
    @Test
    fun testReleasesEveryBufferAfterTheStreamIsFullyConsumed() {
        val buffers = listOf(pooledBuffer("hello "), pooledBuffer("streaming "), pooledBuffer("world"))

        val text = FluxInputStream.toInputStream(Flux.fromIterable(buffers))
            .use { it.bufferedReader().readText() }

        assert(text == "hello streaming world")
        awaitReleased(buffers, "buffers of a fully consumed stream")
    }

    /** Buffers queued in the `publishOn` prefetch at cancellation are discarded, not emitted. */
    @Test
    fun testReleasesBuffersDiscardedWhenTheConsumerClosesEarly() {
        val emitted = CopyOnWriteArrayList<DataBuffer>()
        // More chunks than the pipe can hold, so some sit in the prefetch queue when we cancel.
        val flux = Flux.fromIterable((1..200).map { pooledBuffer("chunk-$it-") })
            .doOnNext { emitted.add(it) }

        val stream = FluxInputStream.toInputStream(flux)
        stream.readNBytes(8)
        stream.close()

        assert(emitted.isNotEmpty()) { "expected the producer to have emitted at least one buffer" }
        awaitReleased(emitted, "emitted buffers after an early close")
    }
}
