package org.edu_sharing.rendering.modules

import org.edu_sharing.rendering.edusharingRepo.services.ContentTransferService
import org.edu_sharing.rendering.storage.StorageService
import org.slf4j.LoggerFactory
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.bodyToFlux
import org.springframework.web.util.UriComponentsBuilder
import org.edu_sharing.rendering.utils.LogSanitizer
import org.springframework.http.HttpHeaders
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import java.io.File
import java.time.Duration
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicInteger
import java.nio.file.StandardOpenOption

@Component
class ConverterWebServiceCaller(
    private val contentTransferService: ContentTransferService,
    private val storageImplementation: StorageService,
    private val properties: ConverterCallProperties = ConverterCallProperties()
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Posts [originalFile] to the converter and streams the result into [convertedFile], retrying transient
     * failures (see [ConverterCallProperties]). Every attempt builds its multipart body anew from the file
     * (a consumed body cannot be replayed) and truncates the result file (a failed attempt may have written
     * part of it). Terminal failures surface as [ConverterCallException].
     */
    private fun postWithRetry(arguments: ConverterWebServiceArguments, originalFile: File, convertedFile: File) {
        val path = arguments.externalServiceMethodPath
        val attempts = AtomicInteger()
        val startedAt = System.nanoTime()
        val attempt = Mono.defer {
            val attemptNumber = attempts.incrementAndGet()
            val builder = MultipartBodyBuilder()
            builder.part("file", FileSystemResource(originalFile))
            arguments.urlParams.forEach { builder.part(it.key, it.value) }
            log.debug("Converter call attempt {}/{}: path='{}'", attemptNumber, properties.maxAttempts, path)
            arguments.client.post()
                .uri {
                    UriComponentsBuilder.fromUri(it.build()).path(path).build(true).toUri()
                }.contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchangeToMono { response ->
                    if (response.statusCode().is2xxSuccessful) {
                        // Stream to disk buffer by buffer; the result is never held in the heap.
                        DataBufferUtils.write(
                            response.bodyToFlux<DataBuffer>(), convertedFile.toPath(),
                            StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING
                        ).then(Mono.just(true))
                    } else {
                        response.createException().flatMap { Mono.error<Boolean>(it) }
                    }
                }
                .timeout(properties.attemptTimeout)
        }
        try {
            attempt
                .retryWhen(Retry.from { signals ->
                    signals.concatMap { signal -> retryDelay(signal, startedAt, path) }
                })
                .timeout(properties.totalBudget)
                .block()
        } catch (exception: Exception) {
            throw ConverterCallException.of(exception, attempts.get(), path)
        }
    }

    /** Emits after the pause before the next attempt, or errors with the failure if it must not be retried. */
    private fun retryDelay(signal: Retry.RetrySignal, startedAt: Long, path: String): Mono<Long> {
        val failure = signal.failure()
        val attemptsDone = signal.totalRetries() + 1
        if (!ConverterCallException.isTransient(failure) || attemptsDone >= properties.maxAttempts) {
            return Mono.error(failure)
        }
        val exponential = properties.initialBackoff.toMillis() * (1L shl (attemptsDone - 1).toInt().coerceAtMost(20))
        val base = exponential.coerceAtMost(properties.maxBackoff.toMillis())
        // Equal jitter: half fixed, half random, so concurrent callers do not retry in lockstep.
        val jittered = base / 2 + ThreadLocalRandom.current().nextLong(base / 2 + 1)
        val retryAfter = retryAfterOf(failure)?.coerceAtMost(properties.maxRetryAfter.toMillis()) ?: 0L
        val delay = maxOf(jittered, retryAfter)
        val elapsed = Duration.ofNanos(System.nanoTime() - startedAt)
        if (elapsed.plusMillis(delay) >= properties.totalBudget) {
            log.warn("Converter call '{}' not retried: budget {} would be exceeded", path, properties.totalBudget)
            return Mono.error(failure)
        }
        log.warn(
            "Converter call '{}' failed transiently (attempt {}/{}), retrying in {} ms: {}",
            path, attemptsDone, properties.maxAttempts, delay, LogSanitizer.describe(failure)
        )
        return Mono.delay(Duration.ofMillis(delay))
    }

    private fun retryAfterOf(failure: Throwable): Long? =
        ConverterCallException.causes(failure).filterIsInstance<WebClientResponseException>().firstOrNull()
            ?.headers?.getFirst(HttpHeaders.RETRY_AFTER)?.trim()?.toLongOrNull()
            ?.takeIf { it >= 0 }?.times(1000)

    fun callConverterService(arguments: ConverterWebServiceArguments) {
        val originalFile = File.createTempFile(
            "${arguments.cacheObject.nodeId.substringBefore(".")}_${arguments.cacheObject.hash}",
            arguments.originalFileExtension
        )
        try {
            // Opened inside the try, after the temp file exists: a failure between opening the content
            // stream and reaching `use` would orphan its pooled Netty buffers for the life of the JVM.
            val inputStream = arguments.inputStream ?: contentTransferService.getAsInputStream(arguments.cacheObject)
            inputStream.use {
                originalFile.outputStream().use { outputStream -> inputStream.copyTo(outputStream) }
            }
            log.debug(
                "Calling converter service: path='{}', fileExtension='{}', fileSize={} bytes",
                arguments.externalServiceMethodPath, arguments.originalFileExtension, originalFile.length()
            )

            val convertedFile = File.createTempFile(
                "${arguments.cacheObject.nodeId.substringBefore(".")}_${arguments.cacheObject.hash}_converted",
                null
            )
            try {
                postWithRetry(arguments, originalFile, convertedFile)
                log.debug(
                    "Converter service responded: path='{}', responseSize={} bytes",
                    arguments.externalServiceMethodPath, convertedFile.length()
                )
                if (convertedFile.length() == 0L) {
                    throw Exception("Empty data returned")
                }
                val convertedCacheObject = arguments.cacheObject.copy()
                convertedCacheObject.mimeType = arguments.targetMimeType
                convertedCacheObject.size = convertedFile.length()
                storageImplementation.putObject(convertedCacheObject, { convertedFile.inputStream() })
            } finally {
                convertedFile.delete()
            }
        } finally {
            originalFile.delete()
        }
    }
}
