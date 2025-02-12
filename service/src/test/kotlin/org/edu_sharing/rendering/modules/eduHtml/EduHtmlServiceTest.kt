package org.edu_sharing.rendering.modules.eduHtml

import io.mockk.*
import io.mockk.junit5.MockKExtension
import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.core.dto.ObjectLink
import org.edu_sharing.rendering.core.dto.RenderDataRequest
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.modules.eduhtml.EduHtmlService
import org.edu_sharing.rendering.renderingJob.entity.JobStatus
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.edu_sharing.rendering.renderingJob.queue.RenderingJobMessage
import org.edu_sharing.rendering.renderingJob.repository.RenderingJobRepository
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.edu_sharing.rendering.storage.StaticStorageService
import org.edu_sharing.rendering.testUtils.JobDataProvider
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.amqp.core.AmqpTemplate


@ExtendWith(MockKExtension::class)
class EduHtmlServiceTest {
    private val jobRepoMock = mockk<RenderingJobRepository>()
    private val subJobRepoMock = mockk<SubJobRepository>()
    private val mapperMock = mockk<Mapper>()
    private val amqpTemplateMock = mockk<AmqpTemplate>()
    private val storageMock = mockk< StaticStorageService>()
    private val jobDataProvider = JobDataProvider()

    private lateinit var underTest: EduHtmlService

    private val request = RenderDataRequest(
        nodeId = "dummyNodeId",
        size = 123L,
        type = "dummyType",
        hash = "dummyHash",
        mimeType = "dummyMimeType",
        version = "dummyVersion",
        repoId = "dummyRepoId",
        title = "dummyTitle",
        userData = null,
        replicationSource = null,
        replicationSourceId = null,
        url = null
    )

    @BeforeEach
    fun setup() {
        underTest = EduHtmlService(
            mapper = mapperMock,
            amqpTemplate = amqpTemplateMock,
            jobRepository = jobRepoMock,
            subJobRepository = subJobRepoMock,
            storageImplementation = storageMock,
        )
        underTest.jobRoutingKey = "routingkey"
        underTest.topicExchangeName = "exchange"
        clearAllMocks()
    }

    @Test
    fun testCreateJobReturnsExistingJobIfFound() {
        // Arrange
        val renderingJob = jobDataProvider.getJobWithoutSubJobs("EDUHTML")
        every { jobRepoMock.findAllByEsObjectId("dummyNodeId") } returns listOf(renderingJob)

        // Act
        val result = underTest.createJob(request, "EDUHTML")

        // Assert
        assert(result == JobDataProvider.DUMMY_JOB_ID)

        verify(exactly = 1) { jobRepoMock.findAllByEsObjectId("dummyNodeId") }
        confirmVerified(jobRepoMock)
    }

    @Test
    fun testCreateJobCreatesJobAndReturnsJobIdIfNoExistingJobFound() {
        // Arrange
        val dummyJob = jobDataProvider.getJobWithoutSubJobs()
        every { jobRepoMock.findAllByEsObjectId("dummyNodeId") } returns emptyList()
        every { mapperMock.renderDataRequestToRenderingJob(request, "EDUHTML") } returns dummyJob
        every { jobRepoMock.save(dummyJob) } returns dummyJob
        val subJobSlot = slot<SubJob>()
        every { subJobRepoMock.save(capture(subJobSlot)) } returns mockk<SubJob>()
        val message = RenderingJobMessage(id = dummyJob.id.toString())
        justRun { amqpTemplateMock.convertAndSend("exchange", "routingkey", message) }

        // Act
        val result = underTest.createJob(request, "EDUHTML")

        // Assert
        assert(subJobSlot.captured.routingKey == "routingkey")
        assert(subJobSlot.captured.parent.id.toString() == dummyJob.id.toString())

        assert(result == dummyJob.id.toString())

        verifySequence {
            jobRepoMock.findAllByEsObjectId("dummyNodeId")
            mapperMock.renderDataRequestToRenderingJob(request, "EDUHTML")
            jobRepoMock.save(dummyJob)
            subJobRepoMock.save(any())
            amqpTemplateMock.convertAndSend("exchange", "routingkey", message)
        }
    }

    @Test
    fun testGetObjectLinkReturnsObjectLinkToIndexPathIfCached() {
        // Arrange
        val objectLink = ObjectLink(link = "mylink")
        val cacheObject = mockk<CacheObject>()

        every {
            storageMock.objectExists(cacheObject, "index.html")
        } returns true

        every { storageMock.getObjectLink(cacheObject, "index.html") } returns objectLink

        // Act
        val result = underTest.getObjectLink(cacheObject)

        // Assert
        assert(result == objectLink)

        verifySequence {
            storageMock.objectExists(cacheObject, "index.html")
            storageMock.getObjectLink(cacheObject, "index.html")
        }
    }

    @Test
    fun testGetObjectLinkThrowsExceptionIfGetFilePropertiesFails() {
        // Arrange
        val cacheObject = mockk<CacheObject>()

        every {
            storageMock.objectExists(cacheObject, "index.html")
        } returns false

        // Act and assert
        assertThrows<Exception> { underTest.getObjectLink(cacheObject) }
        verifySequence {
            storageMock.objectExists(cacheObject, "index.html")
        }
    }

    @Test
    fun testCreateJobCreatesJobAndReturnsJobIdIfNonRunningJobFound() {
        // Arrange
        val finishedJob = mockk<RenderingJob>()
        val dummyJob = jobDataProvider.getJobWithoutSubJobs()
        every { finishedJob.status } returns JobStatus.FINISHED
        every { jobRepoMock.findAllByEsObjectId("dummyNodeId") } returns listOf(finishedJob)
        every { mapperMock.renderDataRequestToRenderingJob(request, "EDUHTML") } returns dummyJob
        every { jobRepoMock.save(dummyJob) } returns dummyJob
        val subJobSlot = slot<SubJob>()
        every { subJobRepoMock.save(capture(subJobSlot)) } returns mockk<SubJob>()
        val message = RenderingJobMessage(id = dummyJob.id.toString())
        justRun { amqpTemplateMock.convertAndSend("exchange", "routingkey", message) }

        excludeRecords {
            finishedJob.status
        }

        // Act
        val result = underTest.createJob(request, "EDUHTML")

        // Assert
        assert(subJobSlot.captured.routingKey == "routingkey")
        assert(subJobSlot.captured.parent.id.toString() == dummyJob.id.toString())

        assert(result == dummyJob.id.toString())

        verifySequence {
            jobRepoMock.findAllByEsObjectId("dummyNodeId")
            mapperMock.renderDataRequestToRenderingJob(request, "EDUHTML")
            jobRepoMock.save(dummyJob)
            subJobRepoMock.save(any())
            amqpTemplateMock.convertAndSend("exchange", "routingkey", message)
        }
    }
}
