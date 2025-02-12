package org.edu_sharing.rendering.modules.av.audio

import io.mockk.*
import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.core.dto.ObjectLink
import org.edu_sharing.rendering.core.exception.ResourceNotFoundException
import org.edu_sharing.rendering.renderingJob.MainJobCreationService
import org.edu_sharing.rendering.storage.StorageService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AudioServiceTest {
    private val storageService = mockk<StorageService>()
    private val mainJobCreationService = mockk<MainJobCreationService>()
    private lateinit var service: AudioService

    @BeforeEach
    fun setUp() {
        service = AudioService(
            storageImplementation = storageService,
            mainJobCreationService = mainJobCreationService,
            convertedAudioMimeTypes = listOf("audio/wav", "audio/ogg"),
            bitrate = 100,
        )
    }

    @Test
    fun testIsConversionObjectReturnsTrueIfMimeTypeInList() {
        val cacheObject = mockk<CacheObject>()
        every { cacheObject.mimeType } returns "audio/wav"
        assert(service.isConversionObject(cacheObject))
    }

    @Test
    fun testIsConversionObjectReturnsFalseIfMimeTypeNotInList() {
        val cacheObject = mockk<CacheObject>()
        every { cacheObject.mimeType } returns "audio/mpeg"
        assert(!service.isConversionObject(cacheObject))
    }

    @Test
    fun testGetObjectLinksReturnsLinkIfAlreadyCached() {
        // Arrange
        val cacheObject = CacheObject(nodeId = "123", type = "audio", hash = "abc123", mimeType = "audio/wav", repoId = "repo123")
        val objectLink = ObjectLink(link = "mylink")
        val lookupObject = cacheObject.copy()
        lookupObject.mimeType = "audio/mpeg"
        lookupObject.quality = 100

        every { storageService.getObjectLink(lookupObject) } returns objectLink

        // Act
        val result = service.getObjectLinks(cacheObject)

        // Assert
        assert(result == mutableListOf(objectLink))

        verify (exactly = 1) { storageService.getObjectLink(lookupObject) }

        confirmVerified(storageService)
    }

    @Test
    fun testGetObjectLinksReturnsLinkIfAlreadyCachedAndNotConversionObject() {
        // Arrange
        val cacheObject = CacheObject(nodeId = "123", type = "audio", hash = "abc123", mimeType = "audio/mpeg", repoId = "repo123")
        val objectLink = ObjectLink(link = "mylink")
        val objectLinkList = listOf(objectLink)

        every { storageService.getObjectLink(cacheObject = cacheObject) } returns objectLink

        // Act
        val result = service.getObjectLinks(cacheObject)

        // Assert
        assert(result == objectLinkList)

        verify (exactly = 1) { storageService.getObjectLink(cacheObject = cacheObject) }

        confirmVerified(storageService)
    }

    @Test
    fun testGetObjectLinksReturnsNullIfNotCachedAndConversionObject() {
        // Arrange
        val cacheObject = CacheObject(nodeId = "123", type = "audio", hash = "abc123", mimeType = "audio/wav", repoId = "repo123")
        val lookupObject = cacheObject.copy()
        lookupObject.mimeType = "audio/mpeg"
        lookupObject.quality = 100

        every { storageService.getObjectLink(lookupObject) } throws ResourceNotFoundException("test")

        // Act
        val result = service.getObjectLinks(cacheObject)

        // Assert
        assert(result == null)

        verify(exactly = 1) { storageService.getObjectLink(lookupObject) }
        confirmVerified(storageService)
    }
   @Test
   fun testRetrieveOrCreateJobReturnsExistingJobIdIfFound() {
       // Arrange
       val cacheObject = CacheObject(nodeId = "123", type = "audio", hash = "abc123", repoId = "repo123")
       every { mainJobCreationService.getExistingJobId(cacheObject) } returns "job123"
       // Act
       val result = service.retrieveOrCreateJob(cacheObject, "AUDIO")
       // Assert
       assert(result == "job123")
       verify (exactly = 1) { mainJobCreationService.getExistingJobId(cacheObject) }
       confirmVerified(mainJobCreationService)
   }

   @Test
    fun testRetrieveOrCreateJobCreatesJobIfNoneExisting() {
        // Arrange
        val cacheObject = CacheObject(nodeId = "123", type = "audio", hash = "abc123", repoId = "repo123")
        every { mainJobCreationService.getExistingJobId(cacheObject) } returns null
        every { mainJobCreationService.createMainJob(cacheObject, "AUDIO", listOf(100)) } returns "job123"

        // Act
        val result = service.retrieveOrCreateJob(cacheObject, "AUDIO")

        // Assert
        assert(result == "job123")

        verifySequence {
            mainJobCreationService.getExistingJobId(cacheObject)
            mainJobCreationService.createMainJob(cacheObject, "AUDIO", listOf(100))
        }
        confirmVerified(mainJobCreationService)
    }
}
