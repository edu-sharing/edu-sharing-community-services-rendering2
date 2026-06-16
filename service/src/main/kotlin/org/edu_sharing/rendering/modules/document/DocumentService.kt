package org.edu_sharing.rendering.modules.document

import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.core.dto.ObjectLink
import org.edu_sharing.rendering.core.exception.ResourceNotFoundException
import org.edu_sharing.rendering.renderingJob.MainJobCreationService
import org.edu_sharing.rendering.storage.StorageService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class DocumentService(
    private val storageImplementation: StorageService,
    private val mainJobCreationService: MainJobCreationService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun getObjectLinks(cacheObject: CacheObject, module: DocumentRenderModule): List<ObjectLink>? {
        val lookUpObject = cacheObject.copy()
        lookUpObject.mimeType = module.getTargetMimetype()
        log.debug("getObjectLinks: nodeId=${cacheObject.nodeId}, mimeType=${cacheObject.mimeType}, targetMimeType=${lookUpObject.mimeType}")

        return try {
            listOf(storageImplementation.getObjectLink(lookUpObject).first)
        } catch (_: ResourceNotFoundException) {
            log.debug("No cached document found for nodeId=${cacheObject.nodeId}")
            null
        }
    }

    fun retrieveOrCreateJob(cacheObject: CacheObject, module: DocumentRenderModule): String {
        log.debug("retrieveOrCreateJob: nodeId=${cacheObject.nodeId}, module=${module.module()}")
        return mainJobCreationService.retrieveOrCreateJob(cacheObject, module)
    }
}
