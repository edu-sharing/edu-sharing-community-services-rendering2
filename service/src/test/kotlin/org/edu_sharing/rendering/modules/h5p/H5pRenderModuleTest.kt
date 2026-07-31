package org.edu_sharing.rendering.modules.h5p

import io.mockk.*
import io.mockk.junit5.MockKExtension
import org.edu_sharing.generated.repository.backend.services.rest.client.model.Node
import org.edu_sharing.rendering.edusharingRepo.services.RepositoryRegistrationStorageService
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class H5pRenderModuleTest {
    private val h5pJobServiceMock = mockk<H5pJobService>()
    private val repositoryRegistrationStorageService = mockk<RepositoryRegistrationStorageService>()

    private lateinit var underTest: H5pRenderModule

    @BeforeEach
    fun setUp() {
        underTest = H5pRenderModule(
            33L,
            h5pJobServiceMock,
            repositoryRegistrationStorageService,
            securityEnabled = true
        )
        clearAllMocks()
    }

    @Test
    fun testHandleAlwaysCreatesAJobAndNeverCallsLumi() {
        // handle() runs on the synchronous renderdata path: Mongo + one publish, no lumi call. Whether the
        // package is already imported is decided asynchronously by H5pLookupReceiver.
        // Arrange
        val node = mockk<Node>()
        every { node.ref.id } returns "node123"
        every { node.aspects } returns null
        every { node.content!!.hash } returns "hash123"
        every { h5pJobServiceMock.createJob(node, "H5P") } returns "job123"

        excludeRecords {
            node.ref.id
            node.aspects
            node.content!!.hash
        }

        // Act
        val result = underTest.handle(node)

        // Assert
        assert(result.module == "H5P")
        assert(result.jobId == "job123")
        assert(result.objectLinks?.size == 0)

        verifySequence {
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
