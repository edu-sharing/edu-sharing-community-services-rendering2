package org.edu_sharing.rendering.modules.h5p

import io.mockk.*
import io.mockk.junit5.MockKExtension
import org.edu_sharing.rendering.dto.RenderDataRequest
import org.edu_sharing.rendering.dto.RenderModules
import org.edu_sharing.rendering.dto.mapper.Mapper
import org.edu_sharing.rendering.dto.queue.RenderingJobMessage
import org.edu_sharing.rendering.entity.JobStatus
import org.edu_sharing.rendering.entity.RenderingJob
import org.edu_sharing.rendering.entity.SubJob
import org.edu_sharing.rendering.processing.JobDataProvider
import org.edu_sharing.rendering.repository.mongo.RenderingJobRepository
import org.edu_sharing.rendering.repository.mongo.SubJobRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.amqp.core.AmqpTemplate

@ExtendWith(MockKExtension::class)
class H5pJobServiceTest {
    private val jobRepoMock = mockk<RenderingJobRepository>()
    private val subJobRepoMock = mockk<SubJobRepository>()
    private val mapperMock = mockk<Mapper>()
    private val amqpTemplateMock = mockk<AmqpTemplate>()
    private val jobDataProvider = JobDataProvider()

    private lateinit var underTest: H5pJobService

    private val request = RenderDataRequest(
        nodeId = "dummyNodeId",
        size = 123L,
        type = "dummyType",
        hash = "dummyHash",
        mimeType = "dummyMimeType",
        version = "dummyVersion",
        repoId = "dummyRepoId",
        title = "dummyTitle",
        userData = null
    )

    @BeforeEach
    fun setup() {
        underTest = H5pJobService(
            mapper = mapperMock,
            jobRepository = jobRepoMock,
            subJobRepository = subJobRepoMock,
            amqpTemplate = amqpTemplateMock
        )
        underTest.jobRoutingKey = "routingkey"
        underTest.topicExchangeName = "exchange"
        clearAllMocks()
    }

    @Test
    fun testCreateJobReturnsExistingJobIfFound() {
        // Arrange
        val renderingJob = jobDataProvider.getJobWithoutSubJobs(RenderModules.EDUHTML)
        every { jobRepoMock.findAllByEsObjectId("dummyNodeId") } returns listOf(renderingJob)

        // Act
        val result = underTest.createJob(request, RenderModules.EDUHTML)

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
        every { mapperMock.renderDataRequestToRenderingJob(request, RenderModules.H5P) } returns dummyJob
        every { jobRepoMock.save(dummyJob) } returns mockk<RenderingJob>()
        val subJobSlot = slot<SubJob>()
        every { subJobRepoMock.save(capture(subJobSlot)) } returns mockk<SubJob>()
        val message = RenderingJobMessage(id = dummyJob.id.toString())
        justRun { amqpTemplateMock.convertAndSend("exchange", "routingkey", message) }

        // Act
        val result = underTest.createJob(request, RenderModules.H5P)

        // Assert
        assert(subJobSlot.captured.routingKey == "routingkey")
        assert(subJobSlot.captured.parent.id.toString() == dummyJob.id.toString())

        assert(result == dummyJob.id.toString())

        verifySequence {
            jobRepoMock.findAllByEsObjectId("dummyNodeId")
            mapperMock.renderDataRequestToRenderingJob(request, RenderModules.H5P)
            jobRepoMock.save(dummyJob)
            subJobRepoMock.save(any())
            amqpTemplateMock.convertAndSend("exchange", "routingkey", message)
        }
    }

    @Test
    fun testCreateJobCreatesJobAndReturnsJobIdIfNonRunningJobFound() {
        // Arrange
        val finishedJob = mockk<RenderingJob>()
        val dummyJob = jobDataProvider.getJobWithoutSubJobs()
        every { finishedJob.status } returns JobStatus.FINISHED
        every { jobRepoMock.findAllByEsObjectId("dummyNodeId") } returns listOf(finishedJob)
        every { mapperMock.renderDataRequestToRenderingJob(request, RenderModules.H5P) } returns dummyJob
        every { jobRepoMock.save(dummyJob) } returns mockk<RenderingJob>()
        val subJobSlot = slot<SubJob>()
        every { subJobRepoMock.save(capture(subJobSlot)) } returns mockk<SubJob>()
        val message = RenderingJobMessage(id = dummyJob.id.toString())
        justRun { amqpTemplateMock.convertAndSend("exchange", "routingkey", message) }

        excludeRecords {
            finishedJob.status
        }

        // Act
        val result = underTest.createJob(request, RenderModules.H5P)

        // Assert
        assert(subJobSlot.captured.routingKey == "routingkey")
        assert(subJobSlot.captured.parent.id.toString() == dummyJob.id.toString())

        assert(result == dummyJob.id.toString())

        verifySequence {
            jobRepoMock.findAllByEsObjectId("dummyNodeId")
            mapperMock.renderDataRequestToRenderingJob(request, RenderModules.H5P)
            jobRepoMock.save(dummyJob)
            subJobRepoMock.save(any())
            amqpTemplateMock.convertAndSend("exchange", "routingkey", message)
        }
    }
}