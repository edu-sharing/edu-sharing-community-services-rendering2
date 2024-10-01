package org.edu_sharing.rendering.modules.image

import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.storage.StorageService
import org.junit.jupiter.api.Test
import java.io.File
import javax.imageio.ImageIO


class ImageConversionServiceTest {
    private val storageService: StorageService = mockk()
    private val imageConversionService = ImageConversionService(storageService)

    @Test
    fun testIfConvertCorrectlyProcessesJpgWithLandscapeOrientation() {
        // Arrange
        val file = File("src/test/resources/fixtures/chernihiv.jpg")
        val sourceImage = ImageIO.read(file)
        val cacheObject = prepareCacheObject()
        justRun {storageService.putObject(cacheObject = any(), inputStream = any(), metadata = any())}
        imageConversionService.imageFormat = "jpeg"
        // Act
        imageConversionService.convert(cacheObject, 100, sourceImage)
        // Assert
        verify(exactly = 1) {storageService.putObject(
            cacheObject = any(),
            inputStream = any(),
            metadata = mapOf("height" to "66", "width" to "100"))
        }
        assert(cacheObject.quality == 100)
        assert(cacheObject.mimeType == "image/jpeg")
        confirmVerified(storageService)
    }
    @Test
    fun testIfConvertCorrectlyProcessesPngWithPortraitOrientation() {
        // Arrange
        val file = File("src/test/resources/fixtures/portait.png")
        val sourceImage = ImageIO.read(file)
        val cacheObject = prepareCacheObject()
        justRun {storageService.putObject(cacheObject = any(), inputStream = any(), metadata = any())}
        imageConversionService.imageFormat = "jpeg"
        // Act
        imageConversionService.convert(cacheObject, 100, sourceImage)
        // Assert
        verify(exactly = 1) {storageService.putObject(
            cacheObject = any(),
            inputStream = any(),
            metadata = mapOf("height" to "100", "width" to "70"))
        }
        assert(cacheObject.quality == 100)
        assert(cacheObject.mimeType == "image/jpeg")
        confirmVerified(storageService)
    }


    @Test
    fun testIfFetchSourceImageReturnsImageFetchedByStorageMethod() {
        // Arrange
        val cacheObject = prepareCacheObject()
        val file = File("src/test/resources/fixtures/chernihiv.jpg")
        val inputStream = file.readBytes().inputStream()
        every { storageService.getObjectStream(cacheObject, true) } returns inputStream
        // Act
        val result = imageConversionService.fetchSourceImage(cacheObject)
        // Assert
        assert(result.width == 275)
        assert(result.height == 183)
        verify(exactly = 1) {storageService.getObjectStream(cacheObject, true)}
        confirmVerified(storageService)
    }

    private fun prepareCacheObject(): CacheObject {
        return  CacheObject(
            nodeId = "nodeId",
            hash = "somehash",
            type = "file-image",
            repoId = "repo123"
        )
    }

}
