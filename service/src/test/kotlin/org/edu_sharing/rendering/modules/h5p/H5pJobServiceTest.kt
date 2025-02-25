package org.edu_sharing.rendering.modules.h5p

import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.renderingJob.repository.RenderingJobRepository
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.edu_sharing.rendering.testUtils.JobDataProvider
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

   /* private val request = RenderDataRequest(
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
        every { mapperMock.renderDataRequestToRenderingJob(request, "H5P") } returns dummyJob
        every { jobRepoMock.save(dummyJob) } returns dummyJob
        val subJobSlot = slot<SubJob>()
        every { subJobRepoMock.save(capture(subJobSlot)) } returns mockk<SubJob>()
        val message = RenderingJobMessage(id = dummyJob.id.toString())
        justRun { amqpTemplateMock.convertAndSend("exchange", "routingkey", message) }

        // Act
        val result = underTest.createJob(request, "H5P")

        // Assert
        assert(subJobSlot.captured.routingKey == "routingkey")
        // What is this for?
        //assert(RenderingJob.id.toString() == dummyJob.id.toString())

        assert(result == dummyJob.id.toString())

        verifySequence {
            jobRepoMock.findAllByEsObjectId("dummyNodeId")
            mapperMock.renderDataRequestToRenderingJob(request, "H5P")
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
        every { mapperMock.renderDataRequestToRenderingJob(request, "H5P") } returns dummyJob
        every { jobRepoMock.save(dummyJob) } returns dummyJob
        val subJobSlot = slot<SubJob>()
        every { subJobRepoMock.save(capture(subJobSlot)) } returns mockk<SubJob>()
        val message = RenderingJobMessage(id = dummyJob.id.toString())
        justRun { amqpTemplateMock.convertAndSend("exchange", "routingkey", message) }

        excludeRecords {
            finishedJob.status
        }

        // Act
        val result = underTest.createJob(request, "H5P")

        // Assert
        assert(subJobSlot.captured.routingKey == "routingkey")
        //assert(RenderingJob.id.toString() == dummyJob.id.toString())

        assert(result == dummyJob.id.toString())

        verifySequence {
            jobRepoMock.findAllByEsObjectId("dummyNodeId")
            mapperMock.renderDataRequestToRenderingJob(request, "H5P")
            jobRepoMock.save(dummyJob)
            subJobRepoMock.save(any())
            amqpTemplateMock.convertAndSend("exchange", "routingkey", message)
        }
    }*/
}