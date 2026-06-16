package org.edu_sharing.rendering.modules.image

import org.edu_sharing.rendering.core.annotation.ConditionalOnConverter
import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.storage.StorageService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * ImageConversionService
 */
@ConditionalOnConverter
@Service
class ImageConversionService (
    private val storageImplementation: StorageService,
){
    @Value("\${app.converter.image.format}")
    private val log = LoggerFactory.getLogger(javaClass)
    lateinit var imageFormat: String

    fun convert(cacheObject: CacheObject, size: Int, sourceImage: BufferedImage) {
        log.debug("Converting image: nodeId=${cacheObject.nodeId}, targetSize=$size, format=$imageFormat")
        val originalHeight = sourceImage.height
        val originalWidth = sourceImage.width
        val ratio = originalWidth.toFloat()/originalHeight
        val targetWidth = if (ratio > 1) size else (size * ratio).toInt()
        val targetHeight = if (ratio > 1) (size / ratio).toInt() else size
        log.debug("Computed target dimensions: ${targetWidth}x${targetHeight} from original ${originalWidth}x${originalHeight}")
        val outputImage = sourceImage.getScaledInstance(targetWidth, targetHeight, Image.SCALE_DEFAULT)
        val bufferedOutputImage = BufferedImage(
            outputImage.getWidth(null),
            outputImage.getHeight(null),
            BufferedImage.TYPE_INT_RGB
        )
        bufferedOutputImage.graphics.drawImage(outputImage, 0, 0, null)
        val byteArrayOutputStream = ByteArrayOutputStream()
        byteArrayOutputStream.use {
            ImageIO.write(bufferedOutputImage, imageFormat, byteArrayOutputStream)
            cacheObject.quality = size
            cacheObject.size = byteArrayOutputStream.size().toLong()
            cacheObject.mimeType = "image/${imageFormat}"
            val metadata =  mapOf(
                "height" to targetHeight.toString(),
                "width" to targetWidth.toString()
            )
            // Size known from conversion
            log.debug("Storing converted image: nodeId=${cacheObject.nodeId}, quality=$size, size=${byteArrayOutputStream.size()} bytes")
            storageImplementation.putObject(
                cacheObject = cacheObject,
                inputStream = ByteArrayInputStream(byteArrayOutputStream.toByteArray()),
                metadata = metadata
            )
        }
    }

    fun fetchSourceImage(cacheObject: CacheObject): BufferedImage {
        log.debug("Fetching source image from storage: nodeId=${cacheObject.nodeId}, mimeType=${cacheObject.mimeType}")
        val fileInputStream = storageImplementation.getObjectStream(cacheObject, true)
        fileInputStream.use {
            val sourceImage = ImageIO.read(fileInputStream)
            return sourceImage
        }
    }

    fun deleteTempFile(cacheObject: CacheObject) {
        storageImplementation.removeTempObject(cacheObject)
    }
}
