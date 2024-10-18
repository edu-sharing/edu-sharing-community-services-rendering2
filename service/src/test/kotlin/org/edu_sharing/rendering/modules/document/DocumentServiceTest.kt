package org.edu_sharing.rendering.modules.document

import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifySequence
import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.core.dto.ObjectLink
import org.edu_sharing.rendering.core.exception.ResourceNotFoundException
import org.edu_sharing.rendering.renderingJob.MainJobCreationService
import org.edu_sharing.rendering.storage.StorageService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test


class DocumentServiceTest {

    private val storageService = mockk<StorageService>()
    private val mainJobCreationService = mockk<MainJobCreationService>()
    private val module = mockk<DocumentRenderModule>()
    private lateinit var underTest: DocumentService

    @BeforeEach
    fun setUp() {
        underTest = DocumentService(
            storageImplementation = storageService,
            mainJobCreationService = mainJobCreationService
        )
    }

    @Test
    fun testGetObjectLinksReturnsLinkIfAlreadyCached() {
        // Arrange
        val cacheObject = CacheObject(nodeId = "123", type = "document", hash = "abc123", repoId = "repo123")
        val objectLink = ObjectLink(link = "mylink")
        val lookupObject = cacheObject.copy()
        lookupObject.mimeType = "image/jpeg"

        every { module.getTargetMimetype() } returns "image/jpeg"
        every { storageService.getObjectLink(lookupObject) } returns objectLink

        // Act
        val result = underTest.getObjectLinks(cacheObject, module)

        // Assert
        assert(result == mutableListOf(objectLink))

        verifySequence {
            module.getTargetMimetype()
            storageService.getObjectLink(lookupObject)
        }

        confirmVerified(module, storageService)
    }

    @Test
    fun testGetObjectLinksReturnsEmptyListIfNotCached() {
        val cacheObject = CacheObject(nodeId = "123", type = "document", hash = "abc123", repoId = "repoId")
        val lookupObject = cacheObject.copy()
        lookupObject.mimeType = "image/jpeg"

        every { module.getTargetMimetype() } returns "image/jpeg"
        every { storageService.getObjectLink(lookupObject) } throws ResourceNotFoundException("test")

        val result = underTest.getObjectLinks(cacheObject, module)
        assert(result == null)

        verifySequence {
            module.getTargetMimetype()
            storageService.getObjectLink(lookupObject)
        }

        confirmVerified(module, storageService)
    }

    @Test
    fun testRetrieveOrCreateJobCallsMainJobCreationServiceWithCorrectArguments() {
        // Arrange
        val cacheObject = mockk<CacheObject>()
        val module = mockk<DocumentRenderModule>()

        every {mainJobCreationService.retrieveOrCreateJob(cacheObject, module)} returns "job123"

        // Act
        val result = underTest.retrieveOrCreateJob(cacheObject, module)

        // Assert
        assert(result == "job123")

        verify(exactly = 1) { mainJobCreationService.retrieveOrCreateJob(cacheObject, module) }
        confirmVerified(storageService, mainJobCreationService)
    }
}
