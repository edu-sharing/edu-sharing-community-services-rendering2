package org.edu_sharing.rendering.logic

import org.edu_sharing.rendering.dto.CacheObject
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class ConversionRetrieval {
    @Value("\${app.converter.image.sizes}")
    lateinit var imageSizes: List<Int>

    @Value("\${app.converter.image.format}")
    lateinit var imageFormat: String

    @Value("\${app.converter.image.mimeTypes}")
    lateinit var convertedImageMimeTypes: List<String>

    @Value("\${app.converter.video.resolutions}")
    lateinit var videoResolutions: List<Int>

    @Value("\${app.converter.video.format}")
    lateinit var videoFormat: String

    @Value("\${app.converter.video.mimeTypes}")
    lateinit var convertedVideoMimeTypes: List<String>

    @Value("\${app.converter.audio.mimeTypes}")
    lateinit var convertedAudioMimeTypes: List<String>

    @Value("\${app.converter.audio.bitrate}")
    lateinit var audioBitrate: String
    fun getCacheObjectWithConvertedMimeType(cacheObject: CacheObject): CacheObject {
        if (! checkIsConversionObject(cacheObject)) {
            return cacheObject
        }
        val tempObject = cacheObject.copy()
        tempObject.mimeType = getTargetMimeType(tempObject)
        return tempObject
    }

    fun checkIsConversionObject(cacheObject: CacheObject): Boolean {
        val combinedMimeTypes = convertedImageMimeTypes + convertedVideoMimeTypes + convertedAudioMimeTypes
        return combinedMimeTypes.contains(cacheObject.mimeType)
    }

    fun getMimeTypeSpecificQualities(mimeType: String): List<Int> {
        return when (mimeType.substringBefore("/")) {
            "video" -> if (this::videoResolutions.isInitialized) videoResolutions else emptyList()
            "image" -> if (this::imageSizes.isInitialized) imageSizes else emptyList()
            "audio" -> if (this::audioBitrate.isInitialized) listOf(audioBitrate.toInt()) else emptyList()
            else -> emptyList()
        }
    }

    private fun getTargetMimeType(cacheObject: CacheObject): String {
        return when (cacheObject.mimeType.substringBefore("/")) {
            "video" -> "video/$videoFormat"
            "image" -> "image/$imageFormat"
            "audio" -> "audio/mpeg"
            else -> ""
        }
    }
}