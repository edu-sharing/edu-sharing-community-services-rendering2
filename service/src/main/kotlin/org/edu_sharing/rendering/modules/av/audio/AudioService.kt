package org.edu_sharing.rendering.modules.av.audio

import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.core.dto.ObjectLink
import org.edu_sharing.rendering.core.exception.ResourceNotFoundException
import org.edu_sharing.rendering.renderingJob.MainJobCreationService
import org.edu_sharing.rendering.storage.StorageService
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class AudioService(
    private val storageImplementation: StorageService,
    private val mainJobCreationService: MainJobCreationService
) {
    @Value("\${app.converter.audio.mimeTypes}")
    lateinit var convertedAudioMimeTypes: List<String>

    fun isConversionObject(cacheObject: CacheObject) = convertedAudioMimeTypes.contains(cacheObject.mimeType)

    fun getObjectLinks(cacheObject: CacheObject): List<ObjectLink>? {
        val lookUpObject = cacheObject.copy()
        lookUpObject.mimeType = "audio/mpeg"

        return try {
            listOf(storageImplementation.getObjectLink(
                if (isConversionObject(cacheObject)) lookUpObject else cacheObject)
            )
        } catch (_: ResourceNotFoundException) {
            null
        }
    }

    fun retrieveOrCreateJob(cacheObject: CacheObject, module: String): String {
        return  mainJobCreationService.getExistingJobId(cacheObject)
            ?: mainJobCreationService.createMainJob(
                cacheObject = cacheObject,
                module = module,
                isConversionType = isConversionObject(cacheObject))
    }
}
