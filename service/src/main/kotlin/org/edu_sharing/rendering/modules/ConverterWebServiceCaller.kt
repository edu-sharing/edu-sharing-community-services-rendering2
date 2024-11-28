package org.edu_sharing.rendering.modules

import org.edu_sharing.rendering.core.annotation.ConditionalOnConverter
import org.edu_sharing.rendering.edusharingRepo.services.ContentTransferService
import org.edu_sharing.rendering.storage.StorageService
import org.springframework.core.io.FileSystemResource
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.util.UriComponentsBuilder
import java.io.File
import java.nio.file.Files

@ConditionalOnConverter
@Component
class ConverterWebServiceCaller(
    private val contentTransferService: ContentTransferService,
    private val storageImplementation: StorageService
) {
    fun callConverterService(arguments: ConverterWebServiceArguments) {
        val inputStream = contentTransferService.getAsInputStream(arguments.cacheObject)
        val originalFile = File(
            "${arguments.cacheObject.nodeId.substringBefore(".")}_${arguments.cacheObject.hash}${arguments.originalFileExtension}"
        )
        inputStream.use {
            Files.copy(inputStream, originalFile.toPath())
        }
        val builder = MultipartBodyBuilder()
        builder.part("file", FileSystemResource(originalFile))
        arguments.urlParams.forEach { builder.part(it.key, it.value) }
        try {
            val returnedData = arguments.client.post()
                .uri {
                    UriComponentsBuilder.fromUri(it.build()).path(arguments.externalServiceMethodPath).build(true).toUri()
                }.contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .retrieve()
                .bodyToMono(ByteArray::class.java)
                .block()
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
        } catch (exception: Exception) {
            throw exception
        } finally {
            originalFile.delete()
        }
    }
}