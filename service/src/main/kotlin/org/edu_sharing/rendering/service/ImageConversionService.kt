package org.edu_sharing.rendering.service

import org.edu_sharing.rendering.blobStorage.StorageService
import org.edu_sharing.rendering.dto.CacheObject
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
@Service
class ImageConversionService (
    private val storageImplementation: StorageService,
){
    @Value("\${app.converter.image.format}")
    lateinit var imageFormat: String

    fun convert(cacheObject: CacheObject, size: Int, sourceImage: BufferedImage) {
        val originalHeight = sourceImage.height
        val originalWidth = sourceImage.width
        val ratio = originalWidth.toFloat()/originalHeight
        val targetWidth: Int
        val targetHeight: Int
        if (ratio < 1) {
            targetHeight = size
            targetWidth = (size * ratio).toInt()
        } else {
            targetHeight = size
            targetWidth = (size * ratio).toInt()
        }
        val outputImage = sourceImage.getScaledInstance(targetWidth, targetHeight, Image.SCALE_DEFAULT)
        val bufferedOutputImage = BufferedImage(
            outputImage.getWidth(null),
            outputImage.getHeight(null),
            BufferedImage.TYPE_INT_RGB
        )
        bufferedOutputImage.graphics.drawImage(outputImage, 0, 0, null)
        val byteArrayOutputStream = ByteArrayOutputStream()
        ImageIO.write(bufferedOutputImage, this.imageFormat, byteArrayOutputStream)
        cacheObject.quality = size
        cacheObject.size = byteArrayOutputStream.size().toLong()
        cacheObject.mimeType = "image/${imageFormat}"
        val metadata =  mapOf(
            "height" to targetHeight.toString(),
            "width" to targetWidth.toString()
        )
        this.storageImplementation.putObject(cacheObject, ByteArrayInputStream(byteArrayOutputStream.toByteArray()), metadata)
    }

    fun fetchSourceImage(cacheObject: CacheObject): BufferedImage {
        val fileInputStream = storageImplementation.getObjectStream(cacheObject, true)
        val sourceImage = ImageIO.read(fileInputStream)
        fileInputStream.close()
        return sourceImage
    }
}