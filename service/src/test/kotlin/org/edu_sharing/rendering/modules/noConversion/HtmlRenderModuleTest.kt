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
import org.edu_sharing.rendering.renderingJob.MainJobCreationService
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.edu_sharing.rendering.storage.StorageService
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
            storageService = storageServiceMock)
        clearAllMocks()
    }

    @Test
    fun testHandleReturnsLinksFromDefaultStrategy() {
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
        assert(result.module == "HTML")
        assert(result.jobId == null)

        verifySequence {
            mapperMock.renderDataRequestToCacheObject(request)
            storageServiceMock.getObjectLink(cacheObject)
        }
    }

    @Test
    fun testModuleReturnsHtmlRenderModule() {
        assert(underTest.module() == "HTML")
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
