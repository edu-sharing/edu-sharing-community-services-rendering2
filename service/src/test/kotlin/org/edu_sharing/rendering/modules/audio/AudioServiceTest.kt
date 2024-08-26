package org.edu_sharing.rendering.modules.audio

import io.mockk.*
import org.edu_sharing.rendering.blobStorage.StorageService
import org.edu_sharing.rendering.dto.CacheObject
import org.edu_sharing.rendering.dto.ObjectLink
import org.edu_sharing.rendering.dto.RenderModules
import org.edu_sharing.rendering.exception.ResourceNotFoundException
import org.edu_sharing.rendering.modules.DirectStorageHandler
import org.edu_sharing.rendering.modules.MainJobCreationService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AudioServiceTest {
    private val storageService = mockk<StorageService>()
    private val directStorageHandler = mockk<DirectStorageHandler>()
    private val mainJobCreationService = mockk<MainJobCreationService>()
    private lateinit var service: AudioService

    @BeforeEach
    fun setUp() {
        service = AudioService(
            directStorageHandler = directStorageHandler,
            storageImplementation = storageService,
            mainJobCreationService = mainJobCreationService
        )
        service.convertedAudioMimeTypes = listOf("audio/wav", "audio/ogg")
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
        val cacheObject = CacheObject(nodeId = "123", type = "audio", hash = "abc123", mimeType = "audio/wav")
        val objectLink = ObjectLink(link = "mylink")
        val lookupObject = cacheObject.copy()
        lookupObject.mimeType = "audio/mpeg"

        every { storageService.getObjectLink(lookupObject) } returns objectLink

        // Act
        val result = service.getObjectLinks(cacheObject)

        // Assert
        assert(result == mutableListOf(objectLink))

        verify (exactly = 1) { storageService.getObjectLink(lookupObject) }
        verify (exactly = 0) { directStorageHandler.getObjectLinkList(any()) }

        confirmVerified(directStorageHandler, storageService)
    }

    @Test
    fun testGetObjectLinksReturnsLinkIfAlreadyCachedAndNotConversionObject() {
        // Arrange
        val cacheObject = CacheObject(nodeId = "123", type = "audio", hash = "abc123", mimeType = "audio/mpeg")
        val objectLink = ObjectLink(link = "mylink")
        val objectLinkList = listOf(objectLink)

        every { directStorageHandler.getObjectLinkList(cacheObject) } returns objectLinkList

        // Act
        val result = service.getObjectLinks(cacheObject)

        // Assert
        assert(result == objectLinkList)

        verify (exactly = 1) { directStorageHandler.getObjectLinkList(cacheObject) }

        confirmVerified(directStorageHandler, storageService)
    }

    @Test
    fun testGetObjectLinksReturnsNullIfNotCachedAndConversionObject() {
        // Arrange
        val cacheObject = CacheObject(nodeId = "123", type = "audio", hash = "abc123", mimeType = "audio/wav")
        val lookupObject = cacheObject.copy()
        lookupObject.mimeType = "audio/mpeg"

        every { storageService.getObjectLink(lookupObject) } throws ResourceNotFoundException("test")

        // Act
        val result = service.getObjectLinks(cacheObject)

        // Assert
        assert(result == null)

        verify(exactly = 1) { storageService.getObjectLink(lookupObject) }
        confirmVerified(directStorageHandler, storageService)
    }

    @Test
    fun testRetrieveOrCreateJobReturnsExistingJobIdIfFound() {
        // Arrange
        val cacheObject = CacheObject(nodeId = "123", type = "audio", hash = "abc123")
        every { mainJobCreationService.getExistingJobId(cacheObject) } returns "job123"

        // Act
        val result = service.retrieveOrCreateJob(cacheObject, RenderModules.AUDIO)

        // Assert
        assert(result == "job123")

        verify (exactly = 1) { mainJobCreationService.getExistingJobId(cacheObject) }
        confirmVerified(mainJobCreationService)
    }

    @Test
    fun testRetrieveOrCreateJobCreatesJobIfNoneExisting() {
        // Arrange
        val cacheObject = CacheObject(nodeId = "123", type = "audio", hash = "abc123")
        every { mainJobCreationService.getExistingJobId(cacheObject) } returns null
        every { mainJobCreationService.createMainJob(cacheObject, RenderModules.AUDIO) } returns "job123"

        // Act
        val result = service.retrieveOrCreateJob(cacheObject, RenderModules.AUDIO)

        // Assert
        assert(result == "job123")

        verifySequence {
            mainJobCreationService.getExistingJobId(cacheObject)
            mainJobCreationService.createMainJob(cacheObject, RenderModules.AUDIO)
        }
        confirmVerified(mainJobCreationService)
    }
}
