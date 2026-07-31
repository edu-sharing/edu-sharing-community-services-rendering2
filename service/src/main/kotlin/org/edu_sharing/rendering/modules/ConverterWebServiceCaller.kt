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
import java.io.File
import java.nio.file.StandardOpenOption

@Component
class ConverterWebServiceCaller(
    private val contentTransferService: ContentTransferService,
    private val storageImplementation: StorageService
) {
    private val log = LoggerFactory.getLogger(javaClass)

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

            val builder = MultipartBodyBuilder()
            builder.part("file", FileSystemResource(originalFile))
            arguments.urlParams.forEach { builder.part(it.key, it.value) }

            val convertedFile = File.createTempFile(
                "${arguments.cacheObject.nodeId.substringBefore(".")}_${arguments.cacheObject.hash}_converted",
                null
            )
            try {
                val responseBuffers = arguments.client.post()
                    .uri {
                        UriComponentsBuilder.fromUri(it.build()).path(arguments.externalServiceMethodPath).build(true)
                            .toUri()
                    }.contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(builder.build()))
                    .retrieve()
                    .bodyToFlux<DataBuffer>()
                DataBufferUtils.write(
                    responseBuffers, convertedFile.toPath(),
                    StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING
                ).block()
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
                convertedFile.inputStream().use {
                    storageImplementation.putObject(convertedCacheObject, it)
                }
            } finally {
                convertedFile.delete()
            }
        } finally {
            originalFile.delete()
        }
    }
}
