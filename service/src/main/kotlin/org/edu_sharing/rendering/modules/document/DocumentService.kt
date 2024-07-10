package org.edu_sharing.rendering.modules.document

import org.edu_sharing.rendering.blobStorage.StorageService
import org.edu_sharing.rendering.dto.CacheObject
import org.edu_sharing.rendering.dto.ObjectLink
import org.edu_sharing.rendering.exception.ResourceNotFoundException
import org.edu_sharing.rendering.modules.MainJobCreationService
import org.springframework.stereotype.Service

@Service
class DocumentService(
    private val storageImplementation: StorageService,
    private val mainJobCreationService: MainJobCreationService
) {
    fun getObjectLinks(cacheObject: CacheObject, module: DocumentRenderModule): List<ObjectLink>? {
        val objectLinkList = mutableListOf<ObjectLink>()
        val lookUpObject = cacheObject.copy()
        lookUpObject.mimeType = module.getTargetMimetype()
        try {
            objectLinkList.add(storageImplementation.getObjectLink(lookUpObject))
        } catch (_: ResourceNotFoundException) {
        }
        return objectLinkList.ifEmpty { null }
    }

    fun retrieveOrCreateJob(cacheObject: CacheObject, module: DocumentRenderModule): String {
        val existingJobId = mainJobCreationService.getExistingJobId(cacheObject)
        if (existingJobId != null) return existingJobId
        return mainJobCreationService.createMainJob(cacheObject, module.module())
    }
}