package org.edu_sharing.rendering.modules.video

import org.edu_sharing.rendering.storage.StorageService
import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.core.dto.ObjectLink
import org.edu_sharing.rendering.core.exception.ResourceNotFoundException
import org.edu_sharing.rendering.renderingJob.MainJobCreationService
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class VideoService(
    private val storageImplementation: StorageService,
    private val mainJobCreationService: MainJobCreationService
) {
    @Value("\${app.converter.video.resolutions}")
    lateinit var targetVideoResolutions: List<Int>

    @Value("\${app.converter.video.format}")
    lateinit var targetVideoFormat: String

    @Value("\${app.converter.video.mimeTypes}")
    lateinit var convertedVideoMimeTypes: List<String>

    fun isConversionObject(cacheObject: CacheObject) = convertedVideoMimeTypes.contains(cacheObject.mimeType)

    fun getObjectLinks(cacheObject: CacheObject, resolution: Int? = null): List<ObjectLink>? {
        if (!isConversionObject(cacheObject)) {
            return try {
                listOf(storageImplementation.getObjectLink(cacheObject))
            } catch (_: ResourceNotFoundException) {
                null
            }
        }

        val lookUpObject = cacheObject.copy()
        lookUpObject.mimeType = "video/$targetVideoFormat"

        val requestedResolutions = if (resolution == null) targetVideoResolutions else listOf(resolution)
        return requestedResolutions.mapNotNull {
            lookUpObject.quality = it
            try {
                storageImplementation.getObjectLink(lookUpObject)
            } catch (_: ResourceNotFoundException) {
                null
            }
        }.toList().ifEmpty { null }
    }

    fun getMissingQualities(availableLinks: List<ObjectLink>?): List<Int> {
        if (availableLinks === null) return targetVideoResolutions
        var highestDeterminedQuality = availableLinks.firstOrNull { it.isHighestQuality }?.height
        if (highestDeterminedQuality == null) highestDeterminedQuality = Int.MAX_VALUE
        return targetVideoResolutions.filter {
            it < highestDeterminedQuality && !availableLinks.map { link -> link.height }.contains(it)
        }
    }

    fun retrieveOrCreateJob(cacheObject: CacheObject, module: String, missingQualities: List<Int>): String {
        val existingJobId = mainJobCreationService.getExistingJobId(cacheObject)
        if (existingJobId != null) return existingJobId
        return mainJobCreationService.createMainJob(cacheObject, module, missingQualities)
    }
}
