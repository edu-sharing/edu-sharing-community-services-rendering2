package org.edu_sharing.rendering.modules.h5p

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verifySequence
import org.edu_sharing.rendering.config.H5P_BASE_PATH
import org.edu_sharing.rendering.dto.RenderDataRequest
import org.edu_sharing.rendering.modules.RenderModules
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.edu_sharing.rendering.modules.h5p.lumi.LumiNodeInfoService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class H5pRenderModuleTest {
    private val h5pJobServiceMock = mockk<H5pJobService>()
    private val lumiNodeInfoServiceMock = mockk<LumiNodeInfoService>()

    private lateinit var underTest: H5pRenderModule

    @BeforeEach
    fun setUp() {
        underTest = H5pRenderModule(33L, h5pJobServiceMock, lumiNodeInfoServiceMock)
        underTest.baseUrl = "http://test.com:8000"
        clearAllMocks()
    }

    @Test
    fun testHandleReturnsCachedContentIdIfFoundInRedisCache() {
        // Arrange
        val request = mockk<RenderDataRequest>()
        every { request.nodeId } returns "node123"
        every { request.hash } returns "hash123"
        every { lumiNodeInfoServiceMock.getContentId("node123", "hash123") } returns "lumiid123"

        // Act
        val result = underTest.handle(request)

        // Assert
        assert(result.module == RenderModules.H5P)
        assert(result.objectLinks?.get(0)?.link == "http://test.com:8000$H5P_BASE_PATH/lumiid123")
        assert(result.jobId == null)

        verifySequence {
            request.nodeId
            request.hash
            lumiNodeInfoServiceMock.getContentId("node123", "hash123")
        }
    }

    @Test
    fun testHandleCreatesNewJobIfNotCachedAndReturnsJobId() {
        // Arrange
        val request = mockk<RenderDataRequest>()
        every { request.nodeId } returns "node123"
        every { request.hash } returns "hash123"
        every { lumiNodeInfoServiceMock.getContentId("node123", "hash123") } returns null
        every { h5pJobServiceMock.createJob(request, RenderModules.H5P) } returns "job123"

        // Act
        val result = underTest.handle(request)

        // Assert
        assert(result.module == RenderModules.H5P)
        assert(result.jobId == "job123")
        assert(result.objectLinks?.size == 0)

        verifySequence {
            request.nodeId
            request.hash
            lumiNodeInfoServiceMock.getContentId("node123", "hash123")
            h5pJobServiceMock.createJob(request, RenderModules.H5P)
        }
    }

    @Test
    fun testModuleReturnsH5pModule() {
        assert(underTest.module() == RenderModules.H5P)
    }

    @Test
    fun testGetNodePermissionExpirationTimeReturnsProperTime() {
        assert(underTest.getNodePermissionExpirationTime() == 33L)
    }

    @Test
    fun testGetObjectLinksFromJobDataReturnsSubJobMessage() {
        // Arrange
        val subJob = mockk<SubJob>()
        val job = mockk<RenderingJob>()

        every { subJob.message } returns "mylink"

        // Act
        val result = underTest.getObjectLinkFromJobData(subJob, job)

        // Assert
        assert(result?.link == "mylink")
    }

    @Test
    fun testGetObjectLinksFromJobDataReturnsEmptyStringIfSubjobMessageIsNull() {
        // Arrange
        val subJob = mockk<SubJob>()
        val job = mockk<RenderingJob>()

        every { subJob.message } returns null

        // Act
        val result = underTest.getObjectLinkFromJobData(subJob, job)

        // Assert
        assert(result?.link == "")
    }
}