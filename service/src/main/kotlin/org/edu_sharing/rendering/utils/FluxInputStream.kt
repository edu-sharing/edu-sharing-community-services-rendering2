package org.edu_sharing.rendering.utils

import org.slf4j.LoggerFactory
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferUtils
import reactor.core.publisher.Flux
import reactor.core.scheduler.Schedulers
import java.io.InputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream

object FluxInputStream {

    private const val PIPE_SIZE = 1024 * 64
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Converts a Flux of DataBuffer into an InputStream for streaming data.
     *
     * @param dataStream A reactive Flux stream of DataBuffer objects that need to be converted.
     * @return An InputStream that streams the data from the provided Flux of DataBuffer.
     */
    fun toInputStream(dataStream: Flux<DataBuffer>): InputStream {
        val outStream = PipedOutputStream()
        val inStream = PipedInputStream(outStream, PIPE_SIZE)

        DataBufferUtils.write(dataStream, outStream)
            .doOnError { e ->
                log.error("Error while streaming data from repository", e)
            }
            .publishOn(Schedulers.boundedElastic())
            .doFinally {
                try {
                    outStream.close()
                } catch (e: Exception) {
                    log.error("Failed to close PipedOutputStream", e)
                }
            }
            .subscribe()

        return inStream
    }
}
