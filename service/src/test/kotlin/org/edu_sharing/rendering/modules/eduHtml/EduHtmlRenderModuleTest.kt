package org.edu_sharing.rendering.modules.eduHtml

import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.modules.eduhtml.EduHtmlRenderModule
import org.edu_sharing.rendering.modules.eduhtml.EduHtmlService
import org.junit.jupiter.api.extension.ExtendWith


@ExtendWith(MockKExtension::class)
class EduHtmlRenderModuleTest {
    private val eduHtmlServiceMock = mockk<EduHtmlService>()
    private val mapper = mockk<Mapper>()

    private lateinit var underTest: EduHtmlRenderModule

    /*@BeforeEach
    fun setup() {
        underTest = EduHtmlRenderModule(
            nodePermissionExpirationTime = 67L,
            eduHtmlService = eduHtmlServiceMock,
            mapper = mapper
        )
        clearAllMocks()
    }

    @Test
    fun testHandleReturnsLinkIfCached() {
        // Arrange
        val request = mockk<RenderDataRequest>()
        val cacheObject = mockk<CacheObject>()
        every { mapper.renderDataRequestToCacheObject(request) } returns cacheObject
        every { eduHtmlServiceMock.getObjectLink(cacheObject = cacheObject) } returns ObjectLink(link = "mylink")

        // Act
        val result = underTest.handle(request)

        // Assert
        assert(result.module == "EDUHTML")
        assert(result.objectLinks?.get(0)?.link == "mylink")
        assert(result.jobId == null)

        verifySequence {
            mapper.renderDataRequestToCacheObject(request)
            eduHtmlServiceMock.getObjectLink(cacheObject = cacheObject)
        }
    }

    @Test
    fun testHandleCreatesNewJobIfNotCachedAndReturnsJobId() {
        // Arrange
        val request: RenderDataRequest = mockk<RenderDataRequest>()
        val cacheObject = mockk<CacheObject>()
        every { mapper.renderDataRequestToCacheObject(request) } returns cacheObject
        every { eduHtmlServiceMock.getObjectLink(cacheObject) } throws ResourceNotFoundException("testException")
        val jobId = "job123"
        every { eduHtmlServiceMock.createJob(request, "EDUHTML") } returns jobId

        // Act
        val result = underTest.handle(request)

        // Assert
        assert(result.module == "EDUHTML")
        assert(result.objectLinks?.size == 0)
        assert(result.jobId == "job123")

        verifySequence {
            eduHtmlServiceMock.getObjectLink(cacheObject)
            eduHtmlServiceMock.createJob(request, "EDUHTML")
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
    }*/
}
