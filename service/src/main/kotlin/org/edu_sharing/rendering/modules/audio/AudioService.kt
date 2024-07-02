package org.edu_sharing.rendering.modules.audio

import io.minio.errors.ErrorResponseException
import org.edu_sharing.rendering.blobStorage.StorageService
import org.edu_sharing.rendering.dto.CacheObject
import org.edu_sharing.rendering.dto.ObjectLink
import org.edu_sharing.rendering.dto.RenderModules
import org.edu_sharing.rendering.modules.DefaultStrategy
import org.edu_sharing.rendering.modules.MainJobCreationService
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class AudioService(
    private val defaultStrategy: DefaultStrategy,
    private val storageImplementation: StorageService,
    private val mainJobCreationService: MainJobCreationService
) {
    @Value("\${app.converter.audio.mimeTypes}")
    lateinit var convertedAudioMimeTypes: List<String>

    fun isConversionObject(cacheObject: CacheObject): Boolean {
        return convertedAudioMimeTypes.contains(cacheObject.mimeType)
    }

    fun getObjectLinks(cacheObject: CacheObject): List<ObjectLink>? {
        if (!isConversionObject(cacheObject)) return defaultStrategy.getObjectLinkList(cacheObject)
        val objectLinkList = mutableListOf<ObjectLink>()
        val lookUpObject = cacheObject.copy()
        lookUpObject.mimeType = "audio/mpeg"
        try {
            objectLinkList.add(storageImplementation.getObjectLink(lookUpObject))
        } catch (_: ErrorResponseException) {
        }
        return objectLinkList.ifEmpty { null }
    }

    fun retrieveOrCreateJob(cacheObject: CacheObject, module: RenderModules): String {
        val existingJobId = mainJobCreationService.getExistingJobId(cacheObject)
        if (existingJobId != null) return existingJobId
        return mainJobCreationService.createMainJob(cacheObject, module)
    }
}