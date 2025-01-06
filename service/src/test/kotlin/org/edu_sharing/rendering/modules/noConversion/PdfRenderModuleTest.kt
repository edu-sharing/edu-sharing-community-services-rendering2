package org.edu_sharing.rendering.modules.noConversion

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verifySequence
import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.core.dto.ObjectLink
import org.edu_sharing.rendering.core.dto.RenderDataRequest
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.core.exception.ResourceNotFoundException
import org.edu_sharing.rendering.renderingJob.MainJobCreationService
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.edu_sharing.rendering.storage.StorageService
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class PdfRenderModuleTest {
    private val mapperMock = mockk<Mapper>()
    private val storageServiceMock = mockk<StorageService>()
    private val mainJobCreationServiceMock = mockk<MainJobCreationService>()

    private lateinit var underTest: PdfRenderModule

    @BeforeEach
    fun setup() {
        underTest = PdfRenderModule(
            nodePermissionExpirationTime = 55L,
            mapper =  mapperMock,
            storageService = storageServiceMock,
            mainJobCreationService = mainJobCreationServiceMock)
        clearAllMocks()
    }

    @Test
    fun testHandleReturnsLinksAlreadyCached() {
        // Arrange
        val request = mockk<RenderDataRequest>()
        val cacheObject = mockk<CacheObject>()
        val link = ObjectLink(link = "mylink")

        every { mapperMock.renderDataRequestToCacheObject(request) } returns cacheObject
        every { storageServiceMock.getObjectLink(cacheObject)} returns link

        // Act
        val result = underTest.handle(request)

        // Assert
        assert(result.objectLinks?.get(0)?.link == "mylink")
        assert(result.module == "PDF")
        assert(result.jobId == null)

        verifySequence {
            mapperMock.renderDataRequestToCacheObject(request)
            storageServiceMock.getObjectLink(cacheObject)
        }
    }

    @Test
    fun testHandleReturnsExistingJobIfFound() {
        // Arrange
        val request = mockk<RenderDataRequest>()
        val cacheObject = mockk<CacheObject>()

        every { mapperMock.renderDataRequestToCacheObject(request) } returns cacheObject
        every { storageServiceMock.getObjectLink(cacheObject)} throws ResourceNotFoundException("")
        every { mainJobCreationServiceMock.getExistingJobId(cacheObject) } returns "existingJob123"

        // Act
        val result = underTest.handle(request)

        // Assert
        assertTrue(result.objectLinks == null, "Result should not contain any object links")
        assertTrue(result.module == "PDF", "Result module should be PDF, got ${result.module}")
        assertTrue(result.jobId == "existingJob123")

        verifySequence {
            mapperMock.renderDataRequestToCacheObject(request)
            storageServiceMock.getObjectLink(cacheObject)
            mainJobCreationServiceMock.getExistingJobId(cacheObject)
        }
    }

    @Test
    fun testHandleCreatesNewJobIfNoCachedDataFoundAndNoExistingJobFound() {
        // Arrange
        val request = mockk<RenderDataRequest>()
        val cacheObject = mockk<CacheObject>()

        every { mapperMock.renderDataRequestToCacheObject(request) } returns cacheObject
        every { storageServiceMock.getObjectLink(cacheObject)} throws ResourceNotFoundException("")
        every { mainJobCreationServiceMock.getExistingJobId(cacheObject) } returns null
        every { mainJobCreationServiceMock.createMainJob(cacheObject, "PDF") } returns "newJob123"

        // Act
        val result = underTest.handle(request)

        // Assert
        assertTrue(result.objectLinks == null, "Result should not contain any object links")
        assertTrue(result.module == "PDF", "Result module should be PDF, got ${result.module}")
        assertTrue(result.jobId == "newJob123")

        verifySequence {
            mapperMock.renderDataRequestToCacheObject(request)
            storageServiceMock.getObjectLink(cacheObject)
            mainJobCreationServiceMock.getExistingJobId(cacheObject)
            mainJobCreationServiceMock.createMainJob(cacheObject, "PDF")
        }
    }

    @Test
    fun testModuleReturnsHtmlRenderModule() {
        assert(underTest.module() == "PDF")
    }

    @Test
    fun testGetObjectLinkFromJobDataReturnsNull() {
        val subJobMock = mockk<SubJob>()
        val renderingJobMock = mockk<RenderingJob>()
        assert(underTest.getObjectLinkFromJobData(subJobMock, renderingJobMock) == null)
    }

    @Test
    fun testGetNodePermissionExpirationTimeReturnsProperTime() {
        assert(underTest.getNodePermissionExpirationTime() == 55L)
    }
}
