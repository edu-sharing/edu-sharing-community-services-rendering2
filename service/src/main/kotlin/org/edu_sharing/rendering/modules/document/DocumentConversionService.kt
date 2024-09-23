package org.edu_sharing.rendering.modules.document

import org.apache.tika.mime.MimeTypes
import org.edu_sharing.rendering.core.annotation.ConditionalOnConverter
import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.edusharingRepo.services.ContentTransferService
import org.edu_sharing.rendering.storage.StorageService
import org.springframework.core.io.FileSystemResource
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.util.UriComponentsBuilder
import java.io.File
import java.nio.file.Files

@ConditionalOnConverter
@Service
class DocumentConversionService(
    private val contentTransferService: ContentTransferService,
    private val storageImplementation: StorageService,
    private val documentConverterWebClient: WebClient,
    private val spreadsheetRenderModule: SpreadsheetRenderModule?
) {
    fun convertAndMoveToCache(cacheObject: CacheObject, module: DocumentRenderModule) {
        val inputStream = contentTransferService.getAsInputStream(cacheObject)
        val originalFile = File(
            "${cacheObject.nodeId.substringBefore(".")}_${cacheObject.hash}${getExtensionFromMimeType(cacheObject.mimeType)}"
        )
        inputStream.use {
            Files.copy(inputStream, originalFile.toPath())
        }
        val builder = MultipartBodyBuilder()
        builder.part("file", FileSystemResource(originalFile))
        if (spreadsheetRenderModule != null && module.module() == spreadsheetRenderModule.module()) {
            builder.part("format", "html")
        }
        try {
            val returnedData = documentConverterWebClient.post()
                .uri {
                    UriComponentsBuilder.fromUri(it.build()).path("/conversion").build(true).toUri()
                }.contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .retrieve()
                .bodyToMono(ByteArray::class.java)
                .block()
            val convertedCacheObject = cacheObject.copy()
            convertedCacheObject.mimeType = module.getTargetMimetype()
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

    private fun getExtensionFromMimeType(mimeType: String): String {
        val extension = MimeTypes.getDefaultMimeTypes().forName(mimeType).extension
        if (extension.isBlank()) {
            throw Exception("Mime type $mimeType is not supported")
        }
        return extension
    }
}
