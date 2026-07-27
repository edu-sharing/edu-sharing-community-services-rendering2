package org.edu_sharing.rendering.modules.h5p

import io.mockk.*
import io.mockk.junit5.MockKExtension
import org.edu_sharing.generated.repository.backend.services.rest.client.model.Node
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.renderingJob.entity.RenderingJobStatus
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.edu_sharing.rendering.renderingJob.queue.RenderingJobMessage
import org.edu_sharing.rendering.renderingJob.repository.RenderingJobRepository
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.edu_sharing.rendering.testUtils.JobDataProvider
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.amqp.core.AmqpTemplate

@ExtendWith(MockKExtension::class)
class H5pJobServiceTest {
    private val jobRepository = mockk<RenderingJobRepository>()
    private val subJobRepository = mockk<SubJobRepository>()
    private val mapper = mockk<Mapper>()
    private val amqpTemplate = mockk<AmqpTemplate>()
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
    */

    @BeforeEach
    fun setup() {
        underTest = H5pJobService(
            mapper = mapper,
            jobRepository = jobRepository,
            subJobRepository = subJobRepository,
            amqpTemplate = amqpTemplate,
        )
        underTest.jobRoutingKey = "routingkey"
        underTest.topicExchangeName = "exchange"
        clearAllMocks()
    }

    @Test
    fun testCreateJobReturnsExistingJobIfFound() {
        // Arrange
        val renderingJob = jobDataProvider.getJobWithoutSubJobs("EDUHTML")
        val node = mockk<Node>()

        every { jobRepository.findAllByEsObjectId("dummyNodeId") } returns listOf(renderingJob)
        every { node.ref.id } returns "dummyNodeId"
        every { node.aspects } returns null

        // Act
        val result = underTest.createJob(node, "EDUHTML")

        // Assert
        assert(result == JobDataProvider.DUMMY_JOB_ID)

        verify(exactly = 1) { jobRepository.findAllByEsObjectId("dummyNodeId") }
        confirmVerified(jobRepository)
    }

    @Test
    fun testCreateJobCreatesJobAndReturnsJobIdIfNoExistingJobFound() {
        // Arrange
        val dummyJob = jobDataProvider.getJobWithoutSubJobs()
        val node = mockk<Node>()

        every { node.ref.id } returns "dummyNodeId"
        every { node.aspects } returns null
        every { jobRepository.findAllByEsObjectId("dummyNodeId") } returns emptyList()
        every { mapper.nodeToRenderingJob(node, "H5P", true) } returns dummyJob
        every { jobRepository.save(dummyJob) } returns dummyJob
        val subJobSlot = slot<SubJob>()
        every { subJobRepository.save(capture(subJobSlot)) } returns mockk<SubJob>()
        val message = RenderingJobMessage(id = dummyJob.id.toString())
        justRun { amqpTemplate.convertAndSend("exchange", "routingkey", message) }

        excludeRecords {
            node.ref.id
            node.aspects
        }

        // Act
        val result = underTest.createJob(node, "H5P")

        // Assert
        assert(subJobSlot.captured.routingKey == "routingkey")
        // What is this for?
        //assert(RenderingJob.id.toString() == dummyJob.id.toString())

        assert(result == dummyJob.id.toString())

        verifySequence {
            jobRepository.findAllByEsObjectId("dummyNodeId")
            mapper.nodeToRenderingJob(node, "H5P", true)
            jobRepository.save(dummyJob)
            subJobRepository.save(any())
            amqpTemplate.convertAndSend("exchange", "routingkey", message)
        }
    }

    @Test
    fun testCreateJobCreatesJobAndReturnsJobIdIfNonRunningJobFound() {
        // Arrange
        val finishedJob = mockk<RenderingJob>()
        val dummyJob = jobDataProvider.getJobWithoutSubJobs()
        val node = mockk<Node>()

        every { node.ref.id } returns "dummyNodeId"
        every { node.aspects } returns null
        every { finishedJob.status } returns RenderingJobStatus.FINISHED
        every { jobRepository.findAllByEsObjectId("dummyNodeId") } returns listOf(finishedJob)
        every { mapper.nodeToRenderingJob(node, "H5P", true) } returns dummyJob
        every { jobRepository.save(dummyJob) } returns dummyJob
        val subJobSlot = slot<SubJob>()
        every { subJobRepository.save(capture(subJobSlot)) } returns mockk<SubJob>()
        val message = RenderingJobMessage(id = dummyJob.id.toString())
        justRun { amqpTemplate.convertAndSend("exchange", "routingkey", message) }

        excludeRecords {
            node.ref.id
            node.aspects
            finishedJob.status
        }

        // Act
        val result = underTest.createJob(node, "H5P")

        // Assert
        assert(subJobSlot.captured.routingKey == "routingkey")
        //assert(RenderingJob.id.toString() == dummyJob.id.toString())

        assert(result == dummyJob.id.toString())

        verifySequence {
            jobRepository.findAllByEsObjectId("dummyNodeId")
            mapper.nodeToRenderingJob(node, "H5P", true)
            jobRepository.save(dummyJob)
            subJobRepository.save(any())
            amqpTemplate.convertAndSend("exchange", "routingkey", message)
        }
    }
}