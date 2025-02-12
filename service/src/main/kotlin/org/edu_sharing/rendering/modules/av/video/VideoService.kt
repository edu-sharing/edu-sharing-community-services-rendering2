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

        val requestedResolutions = if (resolution == null) targetVideoResolutions.getResolutions() else listOf(resolution)
        return requestedResolutions.mapNotNull {
            lookUpObject.quality = it
            try {
                storageImplementation.getObjectLink(lookUpObject)
            } catch (_: ResourceNotFoundException) {
                null
            }
        }.toList().ifEmpty { null }
    }

    fun getMissingQualities(availableLinks: List<ObjectLink>?): Collection<Int> {
        if (availableLinks === null) return targetVideoResolutions.getResolutions()
        var highestDeterminedQuality = availableLinks.firstOrNull { it.isHighestQuality }?.height
        if (highestDeterminedQuality == null) highestDeterminedQuality = Int.MAX_VALUE
        return targetVideoResolutions.getResolutions().filter {
            it < highestDeterminedQuality && !availableLinks.map { link -> link.height }.contains(it)
        }
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
