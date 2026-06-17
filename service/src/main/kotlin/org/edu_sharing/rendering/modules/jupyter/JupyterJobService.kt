package org.edu_sharing.rendering.modules.jupyter

import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.core.dto.ObjectLink
import org.edu_sharing.rendering.core.exception.ResourceNotFoundException
import org.edu_sharing.rendering.renderingJob.MainJobCreationService
import org.edu_sharing.rendering.storage.StorageService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class JupyterJobService(
    private val storageImplementation: StorageService,
    private val mainJobCreationService: MainJobCreationService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun getObjectLinks(cacheObject: CacheObject, module: JupyterRenderModule): List<ObjectLink>? {
        log.debug("Looking up Jupyter object links for nodeId={}", cacheObject.nodeId)
        val lookUpObject = cacheObject.copy()
        lookUpObject.mimeType = module.getTargetMimetype()

        return try {
            listOf(storageImplementation.getObjectLink(lookUpObject).first)
        } catch (_: ResourceNotFoundException) {
            null
        }
    }

    fun retrieveOrCreateJob(cacheObject: CacheObject, module: JupyterRenderModule): String {
        log.debug("Retrieving or creating Jupyter job for nodeId={}", cacheObject.nodeId)
        return mainJobCreationService.retrieveOrCreateJob(cacheObject, module)
    }
}