package org.edu_sharing.rendering.modules.image

import org.edu_sharing.rendering.storage.StorageService
import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.core.dto.ObjectLink
import org.edu_sharing.rendering.modules.RenderModules
import org.edu_sharing.rendering.core.exception.ResourceNotFoundException
import org.edu_sharing.rendering.renderingJob.MainJobCreationService
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import kotlin.math.max

@Service
class ImageService(
    private val storageImplementation: StorageService,
    private val mainJobCreationService: MainJobCreationService
) {
    @Value("\${app.converter.image.mimeTypes}")
    lateinit var convertedImageMimeTypes: List<String>

    @Value("\${app.converter.image.sizes}")
    lateinit var targetImageSizes: List<Int>

    @Value("\${app.converter.image.format}")
    lateinit var targetImageFormat: String

    fun isConversionObject(cacheObject: CacheObject) = convertedImageMimeTypes.contains(cacheObject.mimeType)

    fun getObjectLinks(cacheObject: CacheObject, resolution: Int? = null): List<ObjectLink>? {
        if (!isConversionObject(cacheObject)) {
            return try {
                listOf(storageImplementation.getObjectLink(cacheObject))
            } catch (_: ResourceNotFoundException) {
                null
            }
        }

        val lookUpObject = cacheObject.copy()
        lookUpObject.mimeType = "image/$targetImageFormat"

        val requestedResolutions = if (resolution == null) targetImageSizes else listOf(resolution)
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
        if (availableLinks === null) {
            return targetImageSizes
        }

        val availableQualities = availableLinks.map { max(it.height, it.width) }
        return targetImageSizes.filter { !availableQualities.contains(it) }
    }

    fun retrieveOrCreateJob(cacheObject: CacheObject, module: String, missingQualities: List<Int>): String {
        return mainJobCreationService.getExistingJobId(cacheObject)
            ?: mainJobCreationService.createMainJob(
                cacheObject = cacheObject,
                module = module,
                missingQualities = missingQualities,
                isConversionType = isConversionObject(cacheObject)
            )
    }
}
