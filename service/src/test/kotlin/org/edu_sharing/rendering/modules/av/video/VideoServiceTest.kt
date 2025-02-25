package org.edu_sharing.rendering.modules.av.video

import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.renderingJob.MainJobCreationService
import org.edu_sharing.rendering.storage.StorageService
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class VideoServiceTest {
    private val storageService = mockk<StorageService>()
    private val mainJobCreationService = mockk<MainJobCreationService>()
    private val config = VideoConverterConfig()

    private val cacheObject = CacheObject(
        nodeId = "nodeid12",
        type = "video",
        hash = "hash",
        mimeType = "video/mp4",
        repoId = "repo123"
    )

    lateinit var underTest: VideoService

    /*@BeforeEach
    fun setup() {
        config.resolutions = mapOf<String, VideoResolutionItemConfig>(
            "100" to VideoResolutionItemConfig(2),
            "200" to VideoResolutionItemConfig(1)
        )
        underTest = VideoService(
            storageService,
            mainJobCreationService,
            config
        )
        underTest.targetVideoFormat = "mp4"
        underTest.convertedVideoMimeTypes = listOf("video/mp4", "video/mpeg")
        clearAllMocks()
    }

    @Test
    fun testIsConversionObjectReturnsTrueIfInList() {
        val cacheObject = mockk<CacheObject>()
        every { cacheObject.mimeType } returns "video/mp4"
        assert(underTest.isConversionObject(cacheObject))
    }


    @Test
    fun testIsConversionObjectReturnsFalseIfNotInList() {
        val cacheObject = mockk<CacheObject>()
        every { cacheObject.mimeType } returns "video/wmv"
        assert(!underTest.isConversionObject(cacheObject))
    }

    @Test
    fun testGetObjectLinksInvokesDefaultStrategyIfNotConversionType() {
        // Arrange
        val cacheObject = mockk<CacheObject>()
        val link = ObjectLink(link = "link1")
        val expectedLinks = listOf(link)

        every { cacheObject.mimeType } returns "video/wmv"
        every { storageService.getObjectLink(cacheObject = cacheObject) } returns link

        // Act
        val result = underTest.getObjectLinks(cacheObject)

        // Assert
        assert(result == expectedLinks)

        verifySequence {
            cacheObject.mimeType
            storageService.getObjectLink(cacheObject = cacheObject)
        }
    }


    @Test
    fun testGetObjectLinksReturnsLinksForAlreadyCachedLinksWithDefaultResolutionsIfNotProvidedOtherwise() {
        // Arrange

        val lookupObject1 = cacheObject.copy()
        val lookupObject2 = cacheObject.copy()

        lookupObject1.mimeType = "video/mp4"
        lookupObject1.quality = 100

        lookupObject2.mimeType = "video/mp4"
        lookupObject2.quality = 200

        val objectLink1 = ObjectLink(link = "link1")

        every { storageService.getObjectLink(lookupObject1) } returns objectLink1
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

        lookupObject1.mimeType = "video/mp4"
        lookupObject1.quality = 233

        val objectLink1 = ObjectLink(link = "link1")

        every { storageService.getObjectLink(lookupObject1) } returns objectLink1

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

        lookupObject1.mimeType = "video/mp4"
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
                height = 100,
                width = 160,
            ),
        )

        // Act
        val result = underTest.getMissingQualities(availableLinks)

        // Assert
        assert(result == listOf(200))
    }

    @Test
    fun testGetMissingQualitiesConsidersHighestResolutionIfSetInLinks() {
        // Arrange
        val availableLinks = listOf(
            ObjectLink(
                link = "link1",
                height = 100,
                width = 160,
                isHighestQuality = true
            ),
        )
        // Act
        val result = underTest.getMissingQualities(availableLinks)

        // Assert
        assert(result.isEmpty())
    }

    @Test
    fun testRetrieveOrCreateJobReturnsExistingJobIdIfFound() {
        // Arrange
        every { mainJobCreationService.getExistingJobId(cacheObject) } returns "existing-job-id"

        // Act
        val result = underTest.retrieveOrCreateJob(cacheObject,"VIDEO", listOf(1))

        // Assert
        assert(result == "existing-job-id")
    }

    @Test
    fun testRetrieveOrCreateJobReturnsNewJobIdIfCreated() {
        // Arrange
        val missingQualities = listOf(100, 150)

        every { mainJobCreationService.getExistingJobId(cacheObject) } returns null
        every { mainJobCreationService.createMainJob(cacheObject, "VIDEO", missingQualities, true) } returns
                "new-job-id"

        // Act
        val result = underTest.retrieveOrCreateJob(cacheObject, "VIDEO", missingQualities)

        // Assert
        assert(result == "new-job-id")
    }*/
}
