package org.edu_sharing.rendering.modules.pdf

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verifySequence
import org.edu_sharing.rendering.storage.StorageService
import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.core.dto.ObjectLink
import org.edu_sharing.rendering.core.dto.RenderDataRequest
import org.edu_sharing.rendering.modules.RenderModules
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.edu_sharing.rendering.renderingJob.MainJobCreationService
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
            storageImplementation = storageServiceMock,
            mainJobCreationService = mainJobCreationServiceMock)
        clearAllMocks()
    }

    @Test
    fun testHandleReturnsLinksFromDefaultStrategy() {
        // Arrange
        val request = mockk<RenderDataRequest>()
        val cacheObject = mockk<CacheObject>()
        val linkList = listOf(ObjectLink(link = "mylink"))

        every { mapperMock.renderDataRequestToCacheObject(request) } returns cacheObject

        // Act
        val result = underTest.handle(request)

        // Assert
        assert(result.objectLinks?.get(0)?.link == "mylink")
        assert(result.module == RenderModules.PDF)
        assert(result.jobId == null)

        verifySequence {
            mapperMock.renderDataRequestToCacheObject(request)
        }
    }

    @Test
    fun testModuleReturnsHtmlRenderModule() {
        assert(underTest.module() == RenderModules.PDF)
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
