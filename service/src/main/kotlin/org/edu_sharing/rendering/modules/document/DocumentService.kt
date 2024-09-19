package org.edu_sharing.rendering.modules.document

import org.edu_sharing.rendering.storage.StorageService
import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.core.dto.ObjectLink
import org.edu_sharing.rendering.core.exception.ResourceNotFoundException
import org.edu_sharing.rendering.renderingJob.MainJobCreationService
import org.springframework.stereotype.Service

@Service
class DocumentService(
    private val storageImplementation: StorageService,
    private val mainJobCreationService: MainJobCreationService
) {
    fun getObjectLinks(cacheObject: CacheObject, module: DocumentRenderModule): List<ObjectLink>? {
        val lookUpObject = cacheObject.copy()
        lookUpObject.mimeType = module.getTargetMimetype()

        return try {
            listOf(storageImplementation.getObjectLink(lookUpObject))
        } catch (_: ResourceNotFoundException) {
            null
        }
    }

    fun retrieveOrCreateJob(cacheObject: CacheObject, module: DocumentRenderModule): String {
        val existingJobId = mainJobCreationService.getExistingJobId(cacheObject)
        if (existingJobId != null) {
            return existingJobId
        }

        return mainJobCreationService.createMainJob(cacheObject, module.module())
    }
}
