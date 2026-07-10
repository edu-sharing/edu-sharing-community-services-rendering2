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
     * thread. Closing the returned InputStream disposes the underlying subscription, so a
     * client disconnect tears down the upstream Flux and releases its pooled DataBuffers
     * instead of leaking them until GC.
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
                    outStream.close()
                } catch (e: Exception) {
                    log.error("Failed to close PipedOutputStream", e)
                }
            }
            .subscribe()
        disposableRef.set(disposable)

        return inStream
    }
}
