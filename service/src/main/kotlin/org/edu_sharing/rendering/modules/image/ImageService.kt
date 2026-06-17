package org.edu_sharing.rendering.modules.image

import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.core.dto.ObjectLink
import org.edu_sharing.rendering.core.exception.ResourceNotFoundException
import org.edu_sharing.rendering.renderingJob.MainJobCreationService
import org.edu_sharing.rendering.storage.StorageService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import kotlin.math.max

@Service
class ImageService(
    private val storageImplementation: StorageService,
    private val mainJobCreationService: MainJobCreationService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Value($$"${app.converter.image.mimeTypes}")
    lateinit var convertedImageMimeTypes: List<String>

    @Value($$"${app.converter.image.sizes}")
    lateinit var targetImageSizes: List<Int>

    @Value($$"${app.converter.image.format}")
    lateinit var targetImageFormat: String

    fun isConversionObject(cacheObject: CacheObject) = convertedImageMimeTypes.contains(cacheObject.mimeType)

    fun getObjectLinks(cacheObject: CacheObject, resolution: Int? = null): List<ObjectLink>? {
        log.debug("getObjectLinks: nodeId=${cacheObject.nodeId}, mimeType=${cacheObject.mimeType}, resolution=$resolution")
        if (!isConversionObject(cacheObject)) {
            return try {
                listOf(storageImplementation.getObjectLink(cacheObject).first)
            } catch (_: ResourceNotFoundException) {
                log.debug("No cached object found for non-conversion image nodeId=${cacheObject.nodeId}")
                null
            }
        }

        val lookUpObject = cacheObject.copy()
        lookUpObject.mimeType = "image/$targetImageFormat"

        val requestedResolutions = if (resolution == null) targetImageSizes else listOf(resolution)
        return requestedResolutions.mapNotNull {
            lookUpObject.quality = it
            try {
                storageImplementation.getObjectLink(lookUpObject).first
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
        val existingId = mainJobCreationService.getExistingJobId(cacheObject)
        if (existingId != null) {
            log.debug("Reusing existing job $existingId for nodeId=${cacheObject.nodeId}")
            return existingId
        }
        log.debug("Creating new image job for nodeId=${cacheObject.nodeId}, missingQualities=$missingQualities")
        return mainJobCreationService.createMainJob(
                cacheObject = cacheObject,
                module = module,
                missingQualities = missingQualities,
                isConversionType = isConversionObject(cacheObject)
            )
    }

}
