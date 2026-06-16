package org.edu_sharing.rendering.modules.av.video

import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.core.dto.ObjectLink
import org.edu_sharing.rendering.core.exception.ResourceNotFoundException
import org.edu_sharing.rendering.renderingJob.MainJobCreationService
import org.edu_sharing.rendering.storage.StorageService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class VideoService(
    private val storageImplementation: StorageService,
    private val mainJobCreationService: MainJobCreationService,
    private val targetVideoResolutions: VideoConverterConfig
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Value($$"${app.converter.video.format}")
    lateinit var targetVideoFormat: String

    @Value($$"${app.converter.video.mimeTypes}")
    lateinit var convertedVideoMimeTypes: List<String>

    /**
     * Function isConversionObject
     *
     * Determines whether a given node is subject to conversion. This is the case if its mimetype
     * is in the predefined list (app.converter.video.mimeTypes) or its original height (resolution)
     * is in the interval [x,y] ∀ x,y ∈ app.converter.video.resolutions. Videos with resolutions
     * lower than the smallest defined resolution are not converted and cached as is.
     */
    fun isConversionObject(cacheObject: CacheObject): Boolean {
        return convertedVideoMimeTypes.contains(cacheObject.mimeType)
    }

    fun getObjectLinks(cacheObject: CacheObject, resolution: Int? = null, originalHeight: Int? = null): List<ObjectLink>? {
        log.debug("getObjectLinks: nodeId=${cacheObject.nodeId}, mimeType=${cacheObject.mimeType}, resolution=$resolution, originalHeight=$originalHeight")
        // For objects not subject to conversion we simply return the link (if present, null otherwise)
        if (!isConversionObject(cacheObject)) {
            return try {
                listOf(storageImplementation.getObjectLink(cacheObject).first)
            } catch (_: ResourceNotFoundException) {
                log.debug("No cached object for non-conversion video nodeId=${cacheObject.nodeId}")
                null
            }
        }

        val lookUpObject = cacheObject.copy()
        lookUpObject.mimeType = "video/$targetVideoFormat"

        val requestedResolutions = if (resolution == null)
            targetVideoResolutions.getPossibleResolutions(originalHeight) else listOf(resolution)
        return requestedResolutions.mapNotNull {
            lookUpObject.quality = it
            try {
                storageImplementation.getObjectLink(lookUpObject).first
            } catch (_: ResourceNotFoundException) {
                null
            }
        }.toList().ifEmpty { null }
    }

    fun getMissingQualities(availableLinks: List<ObjectLink>?, originalHeight: Int): Collection<Int> {
        val missing = if (availableLinks === null) targetVideoResolutions.getPossibleResolutions(originalHeight)
        else targetVideoResolutions.getPossibleResolutions(originalHeight).filterNot {
            availableLinks.map { link -> link.height }.contains(it)
        }
        log.debug("getMissingQualities: originalHeight=$originalHeight, available=${availableLinks?.size ?: 0}, missing=$missing")
        return missing
    }

    fun retrieveOrCreateJob(cacheObject: CacheObject, module: String, missingQualities: Collection<Int>): String {
        log.info("Creating main job for: ${cacheObject.nodeId}")
        val existingJobId = mainJobCreationService.getExistingJobId(cacheObject)
        if (existingJobId != null) {
            log.info("Existing job found: $existingJobId")
            return existingJobId
        }
        val newJobId = mainJobCreationService.createMainJob(cacheObject, module, missingQualities, isConversionObject(cacheObject))
        log.info("Created main job: $newJobId")
        return newJobId
    }
}
