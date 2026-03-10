package org.edu_sharing.rendering.modules.h5p

import io.mockk.*
import io.mockk.junit5.MockKExtension
import org.edu_sharing.generated.repository.backend.services.rest.client.model.Node
import org.edu_sharing.rendering.config.AppInfo
import org.edu_sharing.rendering.config.H5P_BASE_PATH
import org.edu_sharing.rendering.core.dto.RequestUserData
import org.edu_sharing.rendering.edusharingRepo.services.RepositoryRegistrationStorageService
import org.edu_sharing.rendering.modules.h5p.lumi.LumiContentManagementService
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class H5pRenderModuleTest {
    private val h5pJobServiceMock = mockk<H5pJobService>()
    private val lumiContentManagementServiceMock = mockk<LumiContentManagementService>()
    private val repositoryRegistrationStorageService = mockk<RepositoryRegistrationStorageService>()
    private val appInfo = AppInfo()

    private lateinit var underTest: H5pRenderModule

    @BeforeEach
    fun setUp() {
        appInfo.public = AppInfo.ConnectionInfo("http", "test.com", 8000, "", "http://test.com:8000" )
        underTest = H5pRenderModule(
            33L,
            h5pJobServiceMock,
            lumiContentManagementServiceMock,
            appInfo,
            repositoryRegistrationStorageService,
            securityEnabled = true
        )
        clearAllMocks()
    }

    @Test
    fun testHandleReturnsCachedContentIdIfFoundInRedisCache() {
        // Arrange
        val node = mockk<Node>()
        every { node.ref.id } returns "node123"
        every { node.content.hash } returns "hash123"
        every { lumiContentManagementServiceMock.getContentId("node123", "hash123") } returns "lumiid123"

        excludeRecords {
            node.ref.id
            node.content.hash
        }

        // Act
        val result = underTest.handle(node, mockk<RequestUserData>())

        // Assert
        assert(result.module == "H5P")
        assert(result.objectLinks?.get(0)?.link == "http://test.com:8000$H5P_BASE_PATH/lumiid123")
        assert(result.jobId == null)

        verify(exactly = 1) {
            lumiContentManagementServiceMock.getContentId("node123", "hash123")
        }
    }

    @Test
    fun testHandleCreatesNewJobIfNotCachedAndReturnsJobId() {
        // Arrange
        val node = mockk<Node>()
        every { node.ref.id } returns "node123"
        every { node.content.hash } returns "hash123"
        every { lumiContentManagementServiceMock.getContentId("node123", "hash123") } returns null
        every { h5pJobServiceMock.createJob(node, "H5P") } returns "job123"

        excludeRecords {
            node.ref.id
            node.content.hash
        }

        // Act
        val result = underTest.handle(node, mockk<RequestUserData>())

        // Assert
        assert(result.module == "H5P")
        assert(result.jobId == "job123")
        assert(result.objectLinks?.size == 0)

        verifySequence {
            lumiContentManagementServiceMock.getContentId("node123", "hash123")
            h5pJobServiceMock.createJob(node, "H5P")
        }
    }

    @Test
    fun testModuleReturnsH5pModule() {
        assert(underTest.module() == "H5P")
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
    fun testGetObjectLinksFromJobDataReturnsEmptyStringIfSubJobMessageIsNull() {
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
