package org.edu_sharing.rendering.modules

import io.mockk.*
import io.mockk.junit5.MockKExtension
import org.edu_sharing.rendering.blobStorage.StorageService
import org.edu_sharing.rendering.dto.CacheObject
import org.edu_sharing.rendering.dto.ObjectLink
import org.edu_sharing.rendering.exception.ResourceNotFoundException
import org.edu_sharing.rendering.service.ContentTransferService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.io.InputStream

@ExtendWith(MockKExtension::class)
class DirectStorageHandlerTest {
    private val contentTransferService = mockk<ContentTransferService>()
    private val storageService = mockk<StorageService>()

    lateinit var underTest: DirectStorageHandler

    @BeforeEach
    fun setup() {
        underTest = DirectStorageHandler(contentTransferService, storageService)
    }

    @Test
    fun testGetObjectLinkListReturnsAvailableLinkRightAway() {
        // Arrange
        val cacheObject = mockk<CacheObject>()
        every { storageService.getObjectLink(cacheObject) } returns ObjectLink(link = "mylink")

        // Act
        val result = underTest.getObjectLinkList(cacheObject)

        // Assert
        assert(result.size == 1)
        assert(result[0] == ObjectLink(link = "mylink"))

        verify(exactly = 1) { storageService.getObjectLink(cacheObject) }
        confirmVerified(storageService)
    }

    @Test
    fun testGetObjectLinkPutsNewObjectsIntoCache() {
        // Arrange
        val cacheObject = mockk<CacheObject>()
        val stream = mockk<InputStream>()
        every { storageService.getObjectLink(cacheObject) } throws ResourceNotFoundException("") andThen (ObjectLink(link = "mylink"))
        every { contentTransferService.getAsInputStream(cacheObject) } returns stream
        justRun { storageService.putObject(cacheObject, stream) }

        // Act
        val result = underTest.getObjectLinkList(cacheObject)

        // Assert
        assert(result.size == 1)
        assert(result[0] == ObjectLink(link = "mylink"))

        verifySequence {
            storageService.getObjectLink(cacheObject)
            contentTransferService.getAsInputStream(cacheObject)
            storageService.putObject(cacheObject, stream)
            storageService.getObjectLink(cacheObject)
        }
    }

    @Test
    fun testGetObjectLinkReturnsEmptyLinkIfCachingFails() {
        // Arrange
        val cacheObject = mockk<CacheObject>()
        val stream = mockk<InputStream>()
        every { storageService.getObjectLink(cacheObject) } throws ResourceNotFoundException("")
        every { contentTransferService.getAsInputStream(cacheObject) } returns stream
        justRun { storageService.putObject(cacheObject, stream) }

        // Act
        val result = underTest.getObjectLinkList(cacheObject)

        // Assert
        assert(result.size == 1)
        assert(result[0] == ObjectLink(link = ""))

        verifySequence {
            storageService.getObjectLink(cacheObject)
            contentTransferService.getAsInputStream(cacheObject)
            storageService.putObject(cacheObject, stream)
            storageService.getObjectLink(cacheObject)
        }
    }
}
