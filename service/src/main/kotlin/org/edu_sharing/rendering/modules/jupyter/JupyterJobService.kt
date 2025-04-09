package org.edu_sharing.rendering.modules.jupyter

import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.core.dto.ObjectLink
import org.edu_sharing.rendering.core.exception.ResourceNotFoundException
import org.edu_sharing.rendering.renderingJob.MainJobCreationService
import org.edu_sharing.rendering.storage.StorageService
import org.springframework.stereotype.Service

@Service
class JupyterJobService(
    private val storageImplementation: StorageService,
    private val mainJobCreationService: MainJobCreationService
) {
    fun getObjectLinks(cacheObject: CacheObject, module: JupyterRenderModule): List<ObjectLink>? {
        val lookUpObject = cacheObject.copy()
        lookUpObject.mimeType = module.getTargetMimetype()

        return try {
            listOf(storageImplementation.getObjectLink(lookUpObject).first)
        } catch (_: ResourceNotFoundException) {
            null
        }
    }

    fun retrieveOrCreateJob(cacheObject: CacheObject, module: JupyterRenderModule): String {
        return mainJobCreationService.retrieveOrCreateJob(cacheObject, module)
    }
}