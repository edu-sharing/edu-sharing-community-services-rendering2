package org.edu_sharing.rendering.processing.document

import org.edu_sharing.rendering.blobStorage.StorageService
import org.edu_sharing.rendering.config.annotation.ConditionalOnConverter
import org.edu_sharing.rendering.dto.CacheObject
import org.edu_sharing.rendering.dto.RenderModules
import org.edu_sharing.rendering.modules.document.DocumentRenderModule
import org.edu_sharing.rendering.service.ContentTransferService
import org.edu_sharing.rendering.service.RenderModuleMappingService
import org.slf4j.LoggerFactory
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.BodyExtractors
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.util.UriComponentsBuilder
import java.io.File
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.nio.file.Files

@ConditionalOnConverter
@Service
class DocumentConversionService(
    private val contentTransferService: ContentTransferService,
    private val storageImplementation: StorageService,
    private val documentConverterWebClient: WebClient
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun convertAndMoveToCache(cacheObject: CacheObject, module: DocumentRenderModule) {
        val inputStream = contentTransferService.getAsInputStream(cacheObject)
        val originalFile = File(
            "${cacheObject.nodeId}_${cacheObject.hash}.${getExtensionFromMimeType(cacheObject.mimeType)}"
        )
        inputStream.use {
            Files.copy(inputStream, originalFile.toPath())
        }
        val builder = MultipartBodyBuilder()
        builder.part("file", FileSystemResource(originalFile))
        if (module.module() == RenderModules.SPREADSHEET) {
            builder.part("format", "html")
        }
        val outputStreamPipe = PipedOutputStream()
        val inputStreamPipe = PipedInputStream(outputStreamPipe)
        try {
            val body = documentConverterWebClient
                .post()
                .uri {
                    UriComponentsBuilder.fromUri(it.build()).path("/conversion").build(true).toUri()
                }.contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchangeToFlux { it.body(BodyExtractors.toDataBuffers()) }
            DataBufferUtils.write(body, outputStreamPipe)
                .doOnError { log.error("something went wrong: {}", it.message, it); throw it }
                .subscribe(DataBufferUtils.releaseConsumer())
            val convertedCacheObject = cacheObject.copy()
            convertedCacheObject.mimeType = module.getTargetMimetype()
            storageImplementation.putObject(convertedCacheObject, inputStreamPipe)
        } catch (exception: Exception) {
            throw exception
        } finally {
            originalFile.delete()
        }
    }

    private fun getExtensionFromMimeType(mimeType: String) = when (mimeType) {
        RenderModuleMappingService.DOC -> "doc"
        RenderModuleMappingService.DOCX -> "docx"
        RenderModuleMappingService.PPT -> "ppt"
        RenderModuleMappingService.PPTX -> "pptx"
        RenderModuleMappingService.XLS -> "xls"
        RenderModuleMappingService.XLSX -> "xlsx"
        RenderModuleMappingService.ODT -> "odt"
        RenderModuleMappingService.ODP -> "odp"
        RenderModuleMappingService.ODS -> "ods"
        else -> throw IllegalArgumentException("Unsupported mime type cannot be converted: $mimeType")
    }
}