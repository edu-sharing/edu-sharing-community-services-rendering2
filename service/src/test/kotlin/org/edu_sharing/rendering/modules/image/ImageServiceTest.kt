package org.edu_sharing.rendering.modules.image

import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.core.dto.ObjectLink
import org.edu_sharing.rendering.core.exception.ResourceNotFoundException
import org.edu_sharing.rendering.renderingJob.MainJobCreationService
import org.edu_sharing.rendering.storage.StorageService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class ImageServiceTest {
    private val storageService = mockk<StorageService>()
    private val mainJobService = mockk<MainJobCreationService>()

    private lateinit var underTest: ImageService

    private val cacheObject = CacheObject(
        nodeId = "nodeid",
        type = "image",
        hash = "hash",
        mimeType = "image/png",
        repoId = "repo123"
    )

    @BeforeEach
    fun setup() {
        underTest = ImageService(storageService, mainJobService)
        underTest.convertedImageMimeTypes = listOf("image/jpeg", "image/png")
        underTest.targetImageSizes = listOf(100, 200)
        underTest.targetImageFormat = "jpeg"
    }

    @Test
    fun testIsConversionObjectReturnsTrueIfInList() {
        // Arrange
        val cacheObject = mockk<CacheObject>(relaxed = true)
        every { cacheObject.mimeType } returns "image/jpeg"

        // Act and assert
        assert(underTest.isConversionObject(cacheObject))

        verify(exactly = 1) { cacheObject.mimeType }
    }

    @Test
    fun testIsConversionObjectReturnsFalseIfNotInList() {
        // Arrange
        val cacheObject = mockk<CacheObject>(relaxed = true)
        every { cacheObject.mimeType } returns "image/ogg"

        // Act and assert
        assert(!underTest.isConversionObject(cacheObject))

        verify(exactly = 1) { cacheObject.mimeType }
    }

    @Test
    fun testGetObjectLinksReturnsNullIfNotFoundForNonConversionObject() {
        // Arrange
        val cacheObject = mockk<CacheObject>(relaxed = true)

        every { cacheObject.mimeType } returns "image/gif"
        every { storageService.getObjectLink(cacheObject) } throws ResourceNotFoundException("")

        // Act and assert
        assert(underTest.getObjectLinks(cacheObject) == null)
        verify(exactly = 1) { storageService.getObjectLink(cacheObject) }
        confirmVerified(storageService)
    }

    @Test
    fun testGetObjectLinksUsesDefaultStrategyIfNotConversionObject() {
        // Arrange
        val cacheObjectWithNonConversion = cacheObject.copy()
        cacheObjectWithNonConversion.mimeType = "image/ogg"

        every { storageService.getObjectLink(cacheObjectWithNonConversion) } returns Pair(ObjectLink(link = "mylink"), 0)

        // Act
        val result = underTest.getObjectLinks(cacheObjectWithNonConversion)

        // Assert
        assert(result?.get(0)?.link == "mylink")

        verify(exactly = 1) { storageService.getObjectLink(cacheObjectWithNonConversion) }
        confirmVerified(storageService)
    }

        @Test
        fun testGetObjectLinksReturnsLinksForAlreadyCachedLinksWithDefaultResolutionsIfNotProvidedOtherwise() {
            // Arrange

            val lookupObject1 = cacheObject.copy()
            val lookupObject2 = cacheObject.copy()

            lookupObject1.mimeType = "image/jpeg"
            lookupObject1.quality = 100

            lookupObject2.mimeType = "image/jpeg"
            lookupObject2.quality = 200

            val objectLink1 = ObjectLink(link = "link1")

            every { storageService.getObjectLink(lookupObject1) } returns Pair(objectLink1, 0)
            every { storageService.getObjectLink(lookupObject2) } throws ResourceNotFoundException("")

            // Act
            val result = underTest.getObjectLinks(cacheObject)

            // Assert
            assert(result?.size == 1)
            assert(result?.first() == objectLink1)

            verify(exactly = 2) {
                storageService.getObjectLink(any<CacheObject>())
            }
            confirmVerified(storageService)
        }

        @Test
        fun testGetObjectLinksReturnsLinksForProvidedResolution() {
            // Arrange
            val lookupObject1 = cacheObject.copy()

            lookupObject1.mimeType = "image/jpeg"
            lookupObject1.quality = 233

            val objectLink1 = ObjectLink(link = "link1")

            every { storageService.getObjectLink(lookupObject1) } returns Pair(objectLink1, 0)

            // Act
            val result = underTest.getObjectLinks(cacheObject, 233)

            // Assert
            assert(result?.size == 1)
            assert(result?.first() == objectLink1)

            verify(exactly = 1) {
                storageService.getObjectLink(any<CacheObject>())
            }
            confirmVerified(storageService)
        }

    @Test
    fun testGetObjectLinksReturnsNullIfNoLinksAreFound() {
        // Arrange
        val lookupObject1 = cacheObject.copy()

        lookupObject1.mimeType = "image/jpeg"
        lookupObject1.quality = 233


        every { storageService.getObjectLink(lookupObject1) } throws ResourceNotFoundException("")

        // Act
        val result = underTest.getObjectLinks(cacheObject, 233)

        // Assert
        assert(result == null)

        verify(exactly = 1) {
            storageService.getObjectLink(any<CacheObject>())
        }
        confirmVerified(storageService)
    }

    @Test
    fun getMissingQualitiesReturnsDefaultResolutionsIfNullProvided() {
        assert(underTest.getMissingQualities(null) == listOf(100, 200))
    }

    @Test
    fun testGetMissingQualitiesReturnsOnlyResolutionsNotInAvailableLinks() {
        // Arrange
        val availableLinks = listOf(
            ObjectLink(
                link = "link1",
                height = 50,
                width = 100,
            ),
        )

        // Act
        val result = underTest.getMissingQualities(availableLinks)

        // Assert
        assert(result == listOf(200))
    }

    @Test
    fun testRetrieveOrCreateJobReturnsExistingJobIdIfFound() {
        // Arrange
        every { mainJobService.getExistingJobId(cacheObject) } returns "existing-job-id"

        // Act
        val result = underTest.retrieveOrCreateJob(cacheObject, "IMAGE", listOf(1))

        // Assert
        assert(result == "existing-job-id")
    }

    @Test
    fun testRetrieveOrCreateJobReturnsNewJobIdIfCreated() {
        // Arrange
        val missingQualities = listOf(100, 150)

        every { mainJobService.getExistingJobId(cacheObject) } returns null
        every { mainJobService.createMainJob(cacheObject, "IMAGE", missingQualities, true) } returns
                "new-job-id"

        // Act
        val result = underTest.retrieveOrCreateJob(cacheObject, "IMAGE", missingQualities)

        // Assert
        assert(result == "new-job-id")
    }
}
