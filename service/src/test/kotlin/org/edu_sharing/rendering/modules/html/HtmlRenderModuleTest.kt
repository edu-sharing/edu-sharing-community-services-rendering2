package org.edu_sharing.rendering.modules.html

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verifySequence
import org.edu_sharing.rendering.blobStorage.StorageService
import org.edu_sharing.rendering.dto.CacheObject
import org.edu_sharing.rendering.dto.ObjectLink
import org.edu_sharing.rendering.dto.RenderDataRequest
import org.edu_sharing.rendering.modules.RenderModules
import org.edu_sharing.rendering.dto.mapper.Mapper
import org.edu_sharing.rendering.renderingJobs.entity.RenderingJob
import org.edu_sharing.rendering.renderingJobs.entity.SubJob
import org.edu_sharing.rendering.renderingJobs.MainJobCreationService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class HtmlRenderModuleTest {
    private val mapperMock = mockk<Mapper>()
    private val mainJobCreationServiceMock = mockk<MainJobCreationService>()
    private val storageServiceMock = mockk<StorageService>()

    private lateinit var underTest: HtmlRenderModule

    @BeforeEach
    fun setup() {
        underTest = HtmlRenderModule(
            nodePermissionExpirationTime = 55L,
            mapper = mapperMock,
            mainJobCreationService = mainJobCreationServiceMock,
            storageImplementation = storageServiceMock)
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
        assert(result.module == RenderModules.HTML)
        assert(result.jobId == null)

        verifySequence {
            mapperMock.renderDataRequestToCacheObject(request)
        }
    }

    @Test
    fun testModuleReturnsHtmlRenderModule() {
        assert(underTest.module() == RenderModules.HTML)
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
