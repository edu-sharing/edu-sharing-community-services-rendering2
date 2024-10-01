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
    private lateinit var service: DocumentService

    @BeforeEach
    fun setUp() {
        service = DocumentService(
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
        val result = service.getObjectLinks(cacheObject, module)

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

        val result = service.getObjectLinks(cacheObject, module)
        assert(result == null)

        verifySequence {
            module.getTargetMimetype()
            storageService.getObjectLink(lookupObject)
        }

        confirmVerified(module, storageService)
    }

    @Test
    fun testRetrieveOrCreateJobReturnsExistingJobIdIfFound() {

        // Arrange
        val cacheObject = CacheObject(nodeId = "123", type = "document", hash = "abc123", repoId = "repo123")
        every { mainJobCreationService.getExistingJobId(cacheObject) } returns "job123"

        // Act
        val result = service.retrieveOrCreateJob(cacheObject, module)

        // Assert
        assert(result == "job123")

        verify(exactly = 1) { mainJobCreationService.getExistingJobId(cacheObject) }
        confirmVerified(mainJobCreationService)
    }

    @Test
    fun testRetrieveOrCreateJobCreatesNewJobIfNoExistingFound() {
        // Arrange
        val cacheObject = CacheObject(nodeId = "123", type = "document", hash = "abc123", repoId = "repo123")
        every { mainJobCreationService.getExistingJobId(cacheObject) } returns null
        every { module.module() } returns "DOCUMENT"
        every { mainJobCreationService.createMainJob(cacheObject, "DOCUMENT", emptyList(), true) } returns "job123"

        // Act
        val result = service.retrieveOrCreateJob(cacheObject, module)

        // Assert
        assert(result == "job123")

        verifySequence {
            mainJobCreationService.getExistingJobId(cacheObject)
            module.module()
            mainJobCreationService.createMainJob(cacheObject, "DOCUMENT", emptyList(), true)
        }
    }
}
