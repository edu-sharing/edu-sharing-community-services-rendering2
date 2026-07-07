package org.edu_sharing.rendering.modules.image

import io.mockk.*
import io.mockk.junit5.MockKExtension
import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.core.exception.ConversionException
import org.edu_sharing.rendering.storage.StorageService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

@ExtendWith(MockKExtension::class)
class ImageConversionServiceTest {
    private val storageService: StorageService = mockk()
    private lateinit var underTest: ImageConversionService

    @BeforeEach
    fun setup() {
        underTest = ImageConversionService(storageService)
        underTest.maxPixels = 100_000_000
    }

    @Test
    fun testIfConvertCorrectlyProcessesJpgWithLandscapeOrientation() {
        // Arrange
        val file = File("src/test/resources/fixtures/chernihiv.jpg")
        val sourceImage = ImageIO.read(file)
        val cacheObject = prepareCacheObject()
        justRun {storageService.putObject(cacheObject = any(), inputStream = any(), metadata = any())}
        underTest.imageFormat = "jpeg"
        // Act
        underTest.convert(cacheObject, 100, sourceImage)
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
        underTest.imageFormat = "jpeg"
        // Act
        underTest.convert(cacheObject, 100, sourceImage)
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
        val result = underTest.fetchSourceImage(cacheObject, 1920)
        // Assert
        assert(result.width == 275)
        assert(result.height == 183)
        verify(exactly = 1) {storageService.getObjectStream(cacheObject, true)}
        confirmVerified(storageService)
    }

    @Test
    fun testIfFetchSourceImageSubsamplesLargeSourcesTowardsTargetSize() {
        // The fixture is 275x183; a target of 50 gives factor 275/100 = 2, so the decoded
        // image is ceil(275/2) x ceil(183/2).
        val cacheObject = prepareCacheObject()
        val file = File("src/test/resources/fixtures/chernihiv.jpg")
        every { storageService.getObjectStream(cacheObject, true) } returns file.readBytes().inputStream()
        // Act
        val result = underTest.fetchSourceImage(cacheObject, 50)
        // Assert
        assert(result.width == 138)
        assert(result.height == 92)
        verify(exactly = 1) {storageService.getObjectStream(cacheObject, true)}
        confirmVerified(storageService)
    }

    @Test
    fun testIfFetchSourceImageRejectsImagesExceedingMaxPixels() {
        // The fixture has 275*183 = 50325 pixels, above the lowered limit.
        val cacheObject = prepareCacheObject()
        val file = File("src/test/resources/fixtures/chernihiv.jpg")
        every { storageService.getObjectStream(cacheObject, true) } returns file.readBytes().inputStream()
        underTest.maxPixels = 10_000
        // Act + Assert
        assertThrows<ConversionException> { underTest.fetchSourceImage(cacheObject, 1920) }
        verify(exactly = 1) {storageService.getObjectStream(cacheObject, true)}
        confirmVerified(storageService)
    }

    @Test
    fun testSubsamplingFactorMath() {
        assert(ImageConversionService.subsamplingFactor(275, 1920) == 1)
        assert(ImageConversionService.subsamplingFactor(3840, 1920) == 1)
        assert(ImageConversionService.subsamplingFactor(7680, 1920) == 2)
        assert(ImageConversionService.subsamplingFactor(8000, 1920) == 2)
        assert(ImageConversionService.subsamplingFactor(20000, 800) == 12)
        assert(ImageConversionService.subsamplingFactor(100, 800) == 1)
        assert(ImageConversionService.subsamplingFactor(275, 0) == 1)
    }

    @Test
    fun testApplyExifOrientationLeavesNormalOrientationUnchanged() {
        val image = markerImage()
        val result = underTest.applyExifOrientation(image, 1)
        assert(result === image)
    }

    @Test
    fun testApplyExifOrientationRotates90ClockwiseForOrientation6() {
        // Source is 4x2 with a red marker at top-left (0,0). Rotated 90° clockwise the image
        // becomes 2x4 and the marker moves to the top-right corner.
        val result = underTest.applyExifOrientation(markerImage(), 6)
        assert(result.width == 2)
        assert(result.height == 4)
        assertMarkerAt(result, 1, 0)
    }

    @Test
    fun testApplyExifOrientationRotates90CounterClockwiseForOrientation8() {
        // Rotated 90° counter-clockwise the marker moves to the bottom-left corner.
        val result = underTest.applyExifOrientation(markerImage(), 8)
        assert(result.width == 2)
        assert(result.height == 4)
        assertMarkerAt(result, 0, 3)
    }

    @Test
    fun testApplyExifOrientationRotates180ForOrientation3() {
        // Rotated 180° the marker moves from top-left to bottom-right; dimensions are unchanged.
        val result = underTest.applyExifOrientation(markerImage(), 3)
        assert(result.width == 4)
        assert(result.height == 2)
        assertMarkerAt(result, 3, 1)
    }

    @Test
    fun testApplyExifOrientationFlipsHorizontallyForOrientation2() {
        // Mirrored horizontally the marker moves from top-left to top-right; dimensions unchanged.
        val result = underTest.applyExifOrientation(markerImage(), 2)
        assert(result.width == 4)
        assert(result.height == 2)
        assertMarkerAt(result, 3, 0)
    }

    /** A 4x2 black image with a single red marker pixel at the top-left corner (0,0). */
    private fun markerImage(): BufferedImage {
        val image = BufferedImage(4, 2, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        graphics.color = Color.BLACK
        graphics.fillRect(0, 0, 4, 2)
        graphics.dispose()
        image.setRGB(0, 0, Color.RED.rgb)
        return image
    }

    private fun assertMarkerAt(image: BufferedImage, x: Int, y: Int) {
        assert(image.getRGB(x, y) == Color.RED.rgb) {
            "expected red marker at ($x,$y) but was 0x${Integer.toHexString(image.getRGB(x, y))}"
        }
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
