package org.edu_sharing.rendering.utils

import org.junit.jupiter.api.Test
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import reactor.core.publisher.Flux
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

class FluxInputStreamTest {

    private val bufferFactory = DefaultDataBufferFactory()

    private fun buffer(text: String): DataBuffer =
        bufferFactory.wrap(text.toByteArray(StandardCharsets.UTF_8))

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
}
