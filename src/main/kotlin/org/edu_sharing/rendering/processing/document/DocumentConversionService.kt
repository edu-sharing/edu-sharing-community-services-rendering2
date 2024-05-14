package org.edu_sharing.rendering.processing.document

import org.edu_sharing.rendering.blobStorage.StorageService
import org.edu_sharing.rendering.config.annotation.ConditionalOnConverter
import org.edu_sharing.rendering.dto.CacheObject
import org.edu_sharing.rendering.exception.UnknownSourceFormatException
import org.edu_sharing.rendering.modules.document.DocumentRenderModule
import org.edu_sharing.rendering.service.ContentTransferService
import org.jodconverter.core.document.DefaultDocumentFormatRegistry
import org.jodconverter.core.office.OfficeManager
import org.jodconverter.local.LocalConverter
import org.springframework.stereotype.Service
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@ConditionalOnConverter
@Service
class DocumentConversionService(
    private val officeManager: OfficeManager,
    private val contentTransferService: ContentTransferService,
    private val storageImplementation: StorageService
) {
    fun convertAndMoveToCache(cacheObject: CacheObject, module: DocumentRenderModule) {
        val inputStream = contentTransferService.getAsInputStream(cacheObject)
        val converter = LocalConverter.builder().officeManager(officeManager).build()
        val targetFormat = DefaultDocumentFormatRegistry.getFormatByMediaType(
            module.getTargetMimetype()
        ) ?: throw UnknownSourceFormatException("Unknown target mime type for module: ${module.module()}")
        val sourceFormat = DefaultDocumentFormatRegistry.getFormatByMediaType(
            cacheObject.mimeType
        ) ?: throw UnknownSourceFormatException("Unknown source format for media type ${cacheObject.type}")
        val outputStream = ByteArrayOutputStream()
        converter.convert(inputStream)
            .`as`(sourceFormat)
            .to(outputStream)
            .`as`(targetFormat)
            .execute()
        cacheObject.size = outputStream.size().toLong()
        cacheObject.mimeType = module.getTargetMimetype()
        storageImplementation.putObject(cacheObject, ByteArrayInputStream(outputStream.toByteArray()))
    }
}