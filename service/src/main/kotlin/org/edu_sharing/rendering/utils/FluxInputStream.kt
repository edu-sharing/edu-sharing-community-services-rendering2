package org.edu_sharing.rendering.utils

import org.slf4j.LoggerFactory
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferUtils
import reactor.core.Disposable
import reactor.core.publisher.Flux
import reactor.core.scheduler.Schedulers
import java.io.InputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.atomic.AtomicReference

object FluxInputStream {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Converts a Flux of DataBuffer into an InputStream for streaming data.
     *
     * The blocking pipe writes run on [Schedulers.boundedElastic] (via `publishOn` on the
     * source) so a slow consumer that fills the pipe cannot stall a reactor/Netty event-loop
     * thread.
     *
     * This method owns the buffers it consumes and releases every one of them: emitted buffers via
     * `releaseConsumer()`, buffers still sitting in the `publishOn` prefetch queue at cancellation
     * via `doOnDiscard`. Closing the returned InputStream disposes the subscription, which is what
     * turns an abandoned read (client disconnect, early close) into that cancellation.
     *
     * @param dataStream A reactive Flux stream of DataBuffer objects that need to be converted.
     * @return An InputStream that streams the data from the provided Flux of DataBuffer.
     */
    fun toInputStream(dataStream: Flux<DataBuffer>): InputStream {
        val outStream = PipedOutputStream()
        val disposableRef = AtomicReference<Disposable>()
        // Connect the pipe before subscribing so the async producer never writes to an
        // unconnected PipedOutputStream ("Pipe not connected"). Disposing on close() cancels
        // the subscription and releases any in-flight DataBuffers.
        val inStream = object : PipedInputStream(outStream, PIPE_SIZE) {
            override fun close() {
                try {
                    super.close()
                } finally {
                    disposableRef.get()?.dispose()
                }
            }
        }

        val disposable = DataBufferUtils.write(dataStream.publishOn(Schedulers.boundedElastic()), outStream)
            // The `OutputStream` overload of `write` returns `Flux<DataBuffer>` and passes the
            // buffers through **unreleased** — releasing is the subscriber's job (contrast the
            // `Path` overload, which returns `Mono<Void>` and consumes them itself, as used in
            // ConverterWebServiceCaller). Subscribing without `releaseConsumer()` therefore leaks
            // every buffer of every transfer, on the happy path: these are pooled Netty direct
            // buffers, and with the AdaptiveByteBufAllocator each one pins a 2 MiB chunk, so the
            // process ratchets towards `MaxDirectMemorySize` until unrelated allocations start
            // failing with OutOfDirectMemoryError.
            .doOnDiscard(DataBuffer::class.java) { DataBufferUtils.release(it) }
            .doOnError { e ->
                log.error("Error while streaming data from repository", e)
            }
            .doFinally {
                try {
                    // Safe to close here: thanks to publishOn(boundedElastic()) above, this terminal
                    // callback runs on a boundedElastic worker (completion/error) or on the disposing
                    // servlet thread (cancel) — never a Netty event loop. PipedOutputStream.close() is
                    // also just a synchronized notify, not real blocking I/O.
                    //noinspection BlockingMethodInNonBlockingContext
                    @Suppress("BlockingMethodInNonBlockingContext")
                    outStream.close()
                } catch (e: Exception) {
                    log.error("Failed to close PipedOutputStream", e)
                }
            }
            .subscribe(DataBufferUtils.releaseConsumer())
        disposableRef.set(disposable)

        return inStream
    }
}
