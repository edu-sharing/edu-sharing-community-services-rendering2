package org.edu_sharing.rendering.modules

import org.edu_sharing.rendering.core.annotation.ConditionalOnConverter
import org.edu_sharing.rendering.edusharingRepo.services.ContentTransferService
import org.edu_sharing.rendering.storage.StorageService
import org.slf4j.LoggerFactory
import org.springframework.core.io.FileSystemResource
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.util.UriComponentsBuilder
import java.io.File

@Component
class ConverterWebServiceCaller(
    private val contentTransferService: ContentTransferService,
    private val storageImplementation: StorageService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun callConverterService(arguments: ConverterWebServiceArguments) {
        val inputStream = arguments.inputStream ?: contentTransferService.getAsInputStream(arguments.cacheObject)
        val originalFile = File.createTempFile(
            "${arguments.cacheObject.nodeId.substringBefore(".")}_${arguments.cacheObject.hash}",
            arguments.originalFileExtension
        )
        try {
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

            val returnedData = arguments.client.post()
                .uri {
                    UriComponentsBuilder.fromUri(it.build()).path(arguments.externalServiceMethodPath).build(true)
                        .toUri()
                }.contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .retrieve()
                .bodyToMono(ByteArray::class.java)
                .block()
            log.debug(
                "Converter service responded: path='{}', responseSize={} bytes",
                arguments.externalServiceMethodPath, returnedData?.size ?: 0
            )
            val convertedCacheObject = arguments.cacheObject.copy()
            convertedCacheObject.mimeType = arguments.targetMimeType
            convertedCacheObject.size = -1
            if (returnedData == null) {
                throw Exception("Empty data returned")
            }
            val resultInputStream = returnedData.inputStream()
            resultInputStream.use {
                storageImplementation.putObject(convertedCacheObject, resultInputStream)
            }
        } finally {
            originalFile.delete()
        }
    }
}
