package org.edu_sharing.rendering.modules.av.audio

import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.core.dto.ObjectLink
import org.edu_sharing.rendering.core.exception.ResourceNotFoundException
import org.edu_sharing.rendering.renderingJob.MainJobCreationService
import org.edu_sharing.rendering.storage.StorageService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class AudioService(
    private val storageImplementation: StorageService,
    private val mainJobCreationService: MainJobCreationService,
    @Value($$"${app.converter.audio.bitrate}")
    private val bitrate: Int,
    @Value($$"${app.converter.audio.mimeTypes}")
    private val convertedAudioMimeTypes: List<String>
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun isConversionObject(cacheObject: CacheObject) = convertedAudioMimeTypes.contains(cacheObject.mimeType)

    fun getObjectLinks(cacheObject: CacheObject): List<ObjectLink>? {
        val isConversion = isConversionObject(cacheObject)
        log.debug("getObjectLinks: nodeId=${cacheObject.nodeId}, mimeType=${cacheObject.mimeType}, isConversionObject=$isConversion")
        val lookUpObject = cacheObject.copy()
        lookUpObject.mimeType = "audio/mpeg"
        lookUpObject.quality = bitrate

        return try {
            listOf(storageImplementation.getObjectLink(
                if (isConversion) lookUpObject else cacheObject).first
            )
        } catch (_: ResourceNotFoundException) {
            log.debug("No cached audio found for nodeId=${cacheObject.nodeId}")
            null
        }
    }

    fun retrieveOrCreateJob(cacheObject: CacheObject, module: String): String {
        val existingId = mainJobCreationService.getExistingJobId(cacheObject)
        if (existingId != null) {
            log.debug("Reusing existing audio job $existingId for nodeId=${cacheObject.nodeId}")
            return existingId
        }
        log.debug("Creating new audio job for nodeId=${cacheObject.nodeId}, bitrate=$bitrate")
        return mainJobCreationService.createMainJob(
                cacheObject = cacheObject,
                module = module,
                isConversionType = isConversionObject(cacheObject),
                missingQualities = listOf(bitrate)
            )
    }
}
