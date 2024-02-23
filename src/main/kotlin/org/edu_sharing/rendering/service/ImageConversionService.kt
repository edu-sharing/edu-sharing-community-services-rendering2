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
    private var sourceImage: BufferedImage? = null

    @Value("\${edu_sharing.image_format}")
    lateinit var imageFormat: String

    fun convert(cacheObject: CacheObject, size: Int) {
        if (this.sourceImage == null) {
            this.fetchSourceImage(cacheObject)
        }
        val fileInputStream = storageImplementation.getObjectStream(cacheObject, true)
        val inputImage = ImageIO.read(fileInputStream)
        val originalHeight = this.sourceImage!!.height
        val originalWidth = this.sourceImage!!.width
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
        val outputImage = inputImage.getScaledInstance(targetWidth, targetHeight, Image.SCALE_DEFAULT)
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
        this.storageImplementation.putObject(cacheObject, ByteArrayInputStream(byteArrayOutputStream.toByteArray()))
    }

    private fun fetchSourceImage(cacheObject: CacheObject) {
        val fileInputStream = storageImplementation.getObjectStream(cacheObject, true)
        this.sourceImage = ImageIO.read(fileInputStream)
    }

    fun reset() {
        this.sourceImage = null
    }
}