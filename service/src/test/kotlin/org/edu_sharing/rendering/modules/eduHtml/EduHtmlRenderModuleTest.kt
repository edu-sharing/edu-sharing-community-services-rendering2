package org.edu_sharing.rendering.modules.eduHtml

import io.mockk.*
import io.mockk.junit5.MockKExtension
import org.edu_sharing.rendering.dto.ObjectLink
import org.edu_sharing.rendering.dto.RenderDataRequest
import org.edu_sharing.rendering.dto.RenderModules
import org.edu_sharing.rendering.entity.RenderingJob
import org.edu_sharing.rendering.entity.SubJob
import org.edu_sharing.rendering.exception.ResourceNotFoundException
import org.edu_sharing.rendering.modules.eduhtml.EduHtmlRenderModule
import org.edu_sharing.rendering.modules.eduhtml.EduHtmlService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith


@ExtendWith(MockKExtension::class)
class EduHtmlRenderModuleTest {
    private val eduHtmlServiceMock = mockk<EduHtmlService>()

    private lateinit var underTest: EduHtmlRenderModule

    @BeforeEach
    fun setup() {
        underTest = EduHtmlRenderModule(eduHtmlServiceMock)
        clearAllMocks()
    }

    @Test
    fun testHandleReturnsLinkIfCached() {
        // Arrange
        val request = mockk<RenderDataRequest>()
        every { request.nodeId } returns "node123"
        every { eduHtmlServiceMock.getObjectLink("node123") } returns ObjectLink(link = "mylink")

        // Act
        val result = underTest.handle(request)

        // Assert
        assert(result.module == RenderModules.EDUHTML)
        assert(result.objectLinks?.get(0)?.link == "mylink")
        assert(result.jobId == null)

        verifySequence {
            request.nodeId
            eduHtmlServiceMock.getObjectLink("node123")
        }
    }

    @Test
    fun testHandleCreatesNewJobIfNotCachedAndReturnsJobId() {
        // Arrange
        val request: RenderDataRequest = mockk<RenderDataRequest>()
        every { request.nodeId } returns "node123"
        every { eduHtmlServiceMock.getObjectLink("node123") } throws ResourceNotFoundException("testException")
        val jobId = "job123"
        every { eduHtmlServiceMock.createJob(request, RenderModules.EDUHTML) } returns jobId

        // Act
        val result = underTest.handle(request)

        // Assert
        assert(result.module == RenderModules.EDUHTML)
        assert(result.objectLinks?.size == 0)
        assert(result.jobId == "job123")

        verifySequence {
            request.nodeId
            eduHtmlServiceMock.getObjectLink("node123")
            eduHtmlServiceMock.createJob(request, RenderModules.EDUHTML)
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
        assert(underTest.module() == RenderModules.EDUHTML)
    }
}