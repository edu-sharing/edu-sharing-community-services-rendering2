package org.edu_sharing.rendering.modules.image

import org.edu_sharing.rendering.blobStorage.StorageService
import org.edu_sharing.rendering.dto.CacheObject
import org.edu_sharing.rendering.dto.ObjectLink
import org.edu_sharing.rendering.dto.RenderModules
import org.edu_sharing.rendering.exception.ResourceNotFoundException
import org.edu_sharing.rendering.modules.DirectStorageHandler
import org.edu_sharing.rendering.modules.MainJobCreationService
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import kotlin.math.max

@Service
class ImageService(
    private val directStorageHandler: DirectStorageHandler,
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
        if (!isConversionObject(cacheObject)){
            return directStorageHandler.getObjectLinkList(cacheObject)
        }

        val objectLinkList = mutableListOf<ObjectLink>()
        val lookUpObject = cacheObject.copy()
        lookUpObject.mimeType = "image/$targetImageFormat"

        val requestedResolutions: List<Int> = if (resolution == null) targetImageSizes else listOf(resolution)
        requestedResolutions.forEach {
            lookUpObject.quality = it
            try {
                objectLinkList.add(storageImplementation.getObjectLink(lookUpObject))
            } catch (_: ResourceNotFoundException) {
            }
        }
        return objectLinkList.ifEmpty { null }
    }

    fun getMissingQualities(availableLinks: List<ObjectLink>?): List<Int> {
        if (availableLinks === null) {
            return targetImageSizes
        }

        val availableQualities = availableLinks.map { max(it.height, it.width) }
        return targetImageSizes.filter { !availableQualities.contains(it) }
    }

    fun retrieveOrCreateJob(cacheObject: CacheObject, module: RenderModules, missingQualities: List<Int>): String {
        val existingJobId = mainJobCreationService.getExistingJobId(cacheObject)
        if (existingJobId != null) {
            return existingJobId
        }

        return mainJobCreationService.createMainJob(cacheObject, module, missingQualities)
    }
}
