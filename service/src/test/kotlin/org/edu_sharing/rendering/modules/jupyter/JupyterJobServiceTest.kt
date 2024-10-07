package org.edu_sharing.rendering.modules.jupyter

import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.excludeRecords
import io.mockk.junit5.MockKExtension
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifySequence
import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.core.dto.ObjectLink
import org.edu_sharing.rendering.core.exception.ResourceNotFoundException
import org.edu_sharing.rendering.renderingJob.MainJobCreationService
import org.edu_sharing.rendering.storage.StorageService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class JupyterJobServiceTest {
    private val storageImplementation = mockk<StorageService>()
    private val mainJobCreationService = mockk<MainJobCreationService>()
    private val module = mockk<JupyterRenderModule>()
    private val cacheObject = mockk<CacheObject>()

    lateinit var underTest: JupyterJobService

    @BeforeEach
    fun setup() {
        underTest = JupyterJobService(
            storageImplementation = storageImplementation,
            mainJobCreationService = mainJobCreationService
        )
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun testGetObjectLinksReturnsListOfObjectLinkReturnedByStorageImplementation() {

        // Arrange
        val lookUpObject = mockk<CacheObject>()

        every { cacheObject.copy() } returns lookUpObject
        every { module.getTargetMimetype() } returns "mime/type"
        justRun { lookUpObject.mimeType = "mime/type" }
        every { storageImplementation.getObjectLink(lookUpObject) } returns ObjectLink(link = "myJupyterLink")

        excludeRecords {
            module.getTargetMimetype()
        }

        // Act
        val result = underTest.getObjectLinks(cacheObject, module)

        // Assert
        assert(result?.size == 1)
        assert(result?.get(0)?.link == "myJupyterLink")

        verifySequence {
            cacheObject.copy()
            lookUpObject.mimeType = "mime/type"
            storageImplementation.getObjectLink(lookUpObject)
        }
    }

    @Test
    fun testGetObjectLinksReturnsNullStorageImplementationIfNotInCache() {
        // Arrange
        val lookUpObject = mockk<CacheObject>()

        every { cacheObject.copy() } returns lookUpObject
        every { module.getTargetMimetype() } returns "mime/type"
        justRun { lookUpObject.mimeType = any() }
        every { storageImplementation.getObjectLink(lookUpObject) } throws ResourceNotFoundException("message")

        // Act and assert
        assert(underTest.getObjectLinks(cacheObject, module) == null)
    }

    @Test
    fun testRetrieveOrCreateJobCallsMainJobCreationService() {
        // Arrange
        every { mainJobCreationService.retrieveOrCreateJob(cacheObject, module) } returns "job123"

        // Act
        val result = underTest.retrieveOrCreateJob(cacheObject, module)

        // Assert
        assert(result == "job123")
        verify (exactly = 1) {mainJobCreationService.retrieveOrCreateJob(cacheObject, module)}
        confirmVerified(mainJobCreationService)
    }
}