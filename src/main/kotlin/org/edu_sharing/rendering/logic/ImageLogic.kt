package org.edu_sharing.rendering.logic

import org.edu_sharing.rendering.dto.CacheObject
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class ImageLogic {
    @Value("\${edu_sharing.image_sizes}")
    lateinit var imageSizes: List<Int>

    @Value("\${edu_sharing.image_format}")
    lateinit var imageFormat: String

    @Value("\${edu_sharing.converted_image_mime_types}")
    lateinit var convertedFormats: List<String>
    fun getCacheObjectWithConvertedMimeType(cacheObject: CacheObject): CacheObject {
        if (cacheObject.mimeType.substringBefore("/") !== "image" || !convertedFormats.contains(cacheObject.mimeType)) {
            return cacheObject
        }
        val tempObject = cacheObject.copy()
        tempObject.mimeType = "image/$imageFormat"
        return tempObject
    }

    fun getImageSizeList(): List<Int> {
        return if (this::imageSizes.isInitialized) imageSizes else emptyList()
    }
}