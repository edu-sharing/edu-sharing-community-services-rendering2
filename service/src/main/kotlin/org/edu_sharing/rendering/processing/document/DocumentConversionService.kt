package org.edu_sharing.rendering.processing.document

import org.edu_sharing.rendering.blobStorage.StorageService
import org.edu_sharing.rendering.config.annotation.ConditionalOnConverter
import org.edu_sharing.rendering.dto.CacheObject
import org.edu_sharing.rendering.modules.document.DocumentRenderModule
import org.edu_sharing.rendering.service.ContentTransferService

import org.springframework.stereotype.Service

@ConditionalOnConverter
@Service
class DocumentConversionService(
    private val contentTransferService: ContentTransferService,
    private val storageImplementation: StorageService
) {
    fun convertAndMoveToCache(cacheObject: CacheObject, module: DocumentRenderModule) {
    }
}