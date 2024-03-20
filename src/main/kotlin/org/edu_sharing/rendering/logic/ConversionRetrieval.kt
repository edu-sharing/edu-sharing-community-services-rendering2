package org.edu_sharing.rendering.logic

import org.edu_sharing.rendering.dto.CacheObject
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class ConversionRetrieval {
    @Value("\${edu_sharing.image_sizes}")
    lateinit var imageSizes: List<Int>

    @Value("\${edu_sharing.image_format}")
    lateinit var imageFormat: String

    @Value("\${edu_sharing.converted_image_mime_types}")
    lateinit var convertedImageMimeTypes: List<String>

    @Value("\${edu_sharing.video_resolutions}")
    lateinit var videoResolutions: List<Int>

    @Value("\${edu_sharing.video_format}")
    lateinit var videoFormat: String

    @Value("\${edu_sharing.converted_video_mime_types}")
    lateinit var convertedVideoMimeTypes: List<String>

    @Value("\${edu_sharing.converted_audio_mime_types}")
    lateinit var convertedAudioMimeTypes: List<String>

    @Value("\${edu_sharing.audio_bitrate}")
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