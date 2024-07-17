package org.edu_sharing.rendering.modules.image

import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import org.edu_sharing.rendering.blobStorage.StorageService
import org.edu_sharing.rendering.dto.CacheObject
import org.edu_sharing.rendering.dto.ObjectLink
import org.edu_sharing.rendering.modules.DefaultStrategy
import org.edu_sharing.rendering.modules.MainJobCreationService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class ImageServiceTest {
    private val defaultStrategyMock = mockk<DefaultStrategy>()
    private val storageServiceMOck = mockk<StorageService>()
    private val mainJobServiceMock = mockk<MainJobCreationService>()

    private lateinit var underTest: ImageService

    private val cacheObject = CacheObject(
        nodeId = "nodeid",
        type = "image",
        hash = "hash",
        mimeType = "image/png"
    )

    @BeforeEach
    fun setup() {
        underTest = ImageService(defaultStrategyMock, storageServiceMOck, mainJobServiceMock)
        underTest.convertedImageMimeTypes = listOf("image/jpeg", "image/png")
        underTest.targetImageSizes = listOf(100,100)
        underTest.targetImageFormat = "jpeg"
    }

    @Test
    fun testIsConversionObjectReturnsTrueIfInList() {
        // Arrange
        val cacheObject = mockk<CacheObject>()
        every { cacheObject.mimeType } returns "image/jpeg"

        // Act and assert
        assert(underTest.isConversionObject(cacheObject))

        verify (exactly = 1) { cacheObject.mimeType }
    }

    @Test
    fun testIsConversionObjectReturnsFalseIfNotInList() {
        // Arrange
        val cacheObject = mockk<CacheObject>()
        every { cacheObject.mimeType } returns "image/ogg"

        // Act and assert
        assert(!underTest.isConversionObject(cacheObject))

        verify (exactly = 1) { cacheObject.mimeType }
    }

    @Test
    fun testGetObjectLinksUsesDefaultStrategyIfNotConversionObject() {
        // Arrange
        val cacheObjectWithNonConversion = cacheObject.copy()
        cacheObjectWithNonConversion.mimeType = "image/ogg"
        every {defaultStrategyMock.getObjectLinkList(cacheObjectWithNonConversion)} returns listOf(ObjectLink(link = "mylink"))

        // Act
        val result = underTest.getObjectLinks(cacheObjectWithNonConversion)

        // Assert
        assert(result?.get(0)?.link == "mylink")

        verify (exactly = 1) { defaultStrategyMock.getObjectLinkList(cacheObjectWithNonConversion) }
        confirmVerified(defaultStrategyMock)
    }
}