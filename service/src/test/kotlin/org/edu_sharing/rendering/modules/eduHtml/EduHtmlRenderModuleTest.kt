package org.edu_sharing.rendering.modules.eduHtml

import io.mockk.*
import io.mockk.junit5.MockKExtension
import org.edu_sharing.generated.repository.backend.services.rest.client.model.Node
import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.core.dto.ObjectLink
import org.edu_sharing.rendering.core.dto.RequestUserData
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.core.exception.ResourceNotFoundException
import org.edu_sharing.rendering.edusharingRepo.services.RepositoryRegistrationStorageService
import org.edu_sharing.rendering.modules.eduhtml.EduHtmlRenderModule
import org.edu_sharing.rendering.modules.eduhtml.EduHtmlService
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith


@ExtendWith(MockKExtension::class)
class EduHtmlRenderModuleTest {
    private val eduHtmlServiceMock = mockk<EduHtmlService>()
    private val repositoryRegistrationStorageServiceMock = mockk<RepositoryRegistrationStorageService>()
    private val mapper = mockk<Mapper>()

    private lateinit var underTest: EduHtmlRenderModule

    @BeforeEach
    fun setup() {
        underTest = EduHtmlRenderModule(
            nodePermissionExpirationTime = 67L,
            eduHtmlService = eduHtmlServiceMock,
            mapper = mapper,
            repositoryRegistrationStorageService = repositoryRegistrationStorageServiceMock
        )
        clearAllMocks()
    }

    @Test
    fun testHandleReturnsLinkIfCached() {
        // Arrange
        val node = mockk<Node>()
        val cacheObject = mockk<CacheObject>()
        every { mapper.nodeToCacheObject(node) } returns cacheObject
        every { eduHtmlServiceMock.getObjectLink(cacheObject = cacheObject) } returns ObjectLink(link = "mylink")

        // Act
        val result = underTest.handle(node, mockk<RequestUserData>())

        // Assert
        assert(result.module == "EDUHTML")
        assert(result.objectLinks?.get(0)?.link == "mylink")
        assert(result.jobId == null)

        verifySequence {
            mapper.nodeToCacheObject(node)
            eduHtmlServiceMock.getObjectLink(cacheObject = cacheObject)
        }
    }

    @Test
    fun testHandleCreatesNewJobIfNotCachedAndReturnsJobId() {
        // Arrange
        val node = mockk<Node>()
        val cacheObject = mockk<CacheObject>()
        every { mapper.nodeToCacheObject(node) } returns cacheObject
        every { eduHtmlServiceMock.getObjectLink(cacheObject) } throws ResourceNotFoundException("testException")
        val jobId = "job123"
        every { eduHtmlServiceMock.createJob(node, "EDUHTML") } returns jobId

        // Act
        val result = underTest.handle(node, mockk<RequestUserData>())

        // Assert
        assert(result.module == "EDUHTML")
        assert(result.objectLinks?.size == 0)
        assert(result.jobId == "job123")

        verifySequence {
            eduHtmlServiceMock.getObjectLink(cacheObject)
            eduHtmlServiceMock.createJob(node, "EDUHTML")
        }
    }

    @Test
    fun testGetObjectLinkFromJobDataConstructsProperObjectLink() {
        // Arrange
        val subJobMock = mockk<SubJob>()
        val renderingJobMock = mockk<RenderingJob>()

        every {subJobMock.message} returns "testmessage"

        // Act
        val result = underTest.getObjectLinkFromJobData(subJobMock, renderingJobMock)

        // Assert
        assert(result?.link == "testmessage")

        verify (exactly = 1) {
            subJobMock.message
        }
    }


    @Test
    fun testModuleReturnsEduHtmlModule() {
        assert(underTest.module() == "EDUHTML")
    }

    @Test
    fun testGetNodePermissionExpirationTimeReturnsSetTime() {
        assert(underTest.getNodePermissionExpirationTime() == 67L)
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
    fun testGetObjectLinksFromJobDataReturnsEmptyStringIfSubJobMessageEmpty() {
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
