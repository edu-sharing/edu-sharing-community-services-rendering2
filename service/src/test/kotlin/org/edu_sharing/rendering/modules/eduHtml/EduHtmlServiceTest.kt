package org.edu_sharing.rendering.modules.eduHtml

import io.mockk.*
import io.mockk.junit5.MockKExtension
import org.bson.types.ObjectId
import org.edu_sharing.generated.repository.backend.services.rest.client.model.Node
import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.core.dto.ObjectLink
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.core.exception.ResourceNotFoundException
import org.edu_sharing.rendering.modules.eduhtml.EduHtmlService
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.RenderingJobStatus
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
import org.springframework.dao.DuplicateKeyException


@ExtendWith(MockKExtension::class)
class EduHtmlServiceTest {
    private val jobRepoMock = mockk<RenderingJobRepository>()
    private val subJobRepoMock = mockk<SubJobRepository>()
    private val mapperMock = mockk<Mapper>()
    private val amqpTemplateMock = mockk<AmqpTemplate>()
    private val storageMock = mockk< StaticStorageService>()
    private val jobDataProvider = JobDataProvider()

    private lateinit var underTest: EduHtmlService

    val node = mockk<Node>(relaxed = true)

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
        every { node.ref.id } returns "dummyNodeId"

        // Act
        val result = underTest.createJob(node, "EDUHTML")

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
        every { mapperMock.nodeToRenderingJob(node, "EDUHTML", true) } returns dummyJob
        every { jobRepoMock.save(dummyJob) } returns dummyJob
        val subJobSlot = slot<SubJob>()
        every { subJobRepoMock.save(capture(subJobSlot)) } returns mockk<SubJob>()
        val message = RenderingJobMessage(id = dummyJob.id.toString())
        justRun { amqpTemplateMock.convertAndSend("exchange", "routingkey", message) }
        every { node.ref.id } returns "dummyNodeId"

        // Act
        val result = underTest.createJob(node, "EDUHTML")

        // Assert
        assert(subJobSlot.captured.routingKey == "routingkey")
        assert(subJobSlot.captured.parent.id.toString() == dummyJob.id.toString())

        assert(result == dummyJob.id.toString())

        verifySequence {
            jobRepoMock.findAllByEsObjectId("dummyNodeId")
            mapperMock.nodeToRenderingJob(node, "EDUHTML", true)
            jobRepoMock.save(dummyJob)
            subJobRepoMock.save(any())
            amqpTemplateMock.convertAndSend("exchange", "routingkey", message)
        }
    }

    @Test
    fun testCreateJobReusesExistingJobWhenConcurrentInsertLosesTheRace() {
        // Arrange: no active job on the first lookup, but a concurrent request inserts one, so our
        // save hits the activeJobPerNodeHash unique index (DuplicateKeyException) and we reuse it.
        val existingJob = jobDataProvider.getJobWithoutSubJobs()
        val newJob = jobDataProvider.getJobWithoutSubJobs(id = ObjectId(JobDataProvider.DUMMY_JOB_ID_2))
        every { node.ref.id } returns "dummyNodeId"
        every { jobRepoMock.findAllByEsObjectId("dummyNodeId") } returnsMany
            listOf(emptyList(), listOf(existingJob))
        every { mapperMock.nodeToRenderingJob(node, "EDUHTML", true) } returns newJob
        every { jobRepoMock.save(newJob) } throws DuplicateKeyException("duplicate active job")

        // Act
        val result = underTest.createJob(node, "EDUHTML")

        // Assert: the existing job's id is returned, and no sub-job / message is created for the loser.
        assert(result == JobDataProvider.DUMMY_JOB_ID)
        verify(exactly = 2) { jobRepoMock.findAllByEsObjectId("dummyNodeId") }
        verify(exactly = 1) { jobRepoMock.save(newJob) }
        verify(exactly = 0) { subJobRepoMock.save(any()) }
        verify(exactly = 0) { amqpTemplateMock.convertAndSend(any<String>(), any<String>(), any<RenderingJobMessage>()) }
    }

    @Test
    fun testGetObjectLinkReturnsObjectLinkToIndexPathIfCached() {
        // Arrange
        val objectLink = ObjectLink(link = "mylink")
        val cacheObject = mockk<CacheObject>(relaxed = true)

        every { storageMock.objectExists(cacheObject, EduHtmlService.COMPLETION_MARKER_PATH) } returns true
        every {
            storageMock.objectExists(cacheObject, "index.html")
        } returns true

        every { storageMock.getObjectLink(cacheObject, "index.html") } returns objectLink

        // Act
        val result = underTest.getObjectLink(cacheObject, listOf("index.html", "index.htm", "story.html"))

        // Assert
        assert(result == objectLink)

        verifySequence {
            storageMock.objectExists(cacheObject, EduHtmlService.COMPLETION_MARKER_PATH)
            storageMock.objectExists(cacheObject, "index.html")
            storageMock.getObjectLink(cacheObject, "index.html")
        }
    }

    @Test
    fun testGetObjectLinkFallsBackToStoryHtmlWhenNoIndex() {
        // Arrange
        val objectLink = ObjectLink(link = "storylink")
        val cacheObject = mockk<CacheObject>(relaxed = true)

        every { storageMock.objectExists(cacheObject, EduHtmlService.COMPLETION_MARKER_PATH) } returns true
        every { storageMock.objectExists(cacheObject, "index.html") } returns false
        every { storageMock.objectExists(cacheObject, "index.htm") } returns false
        every { storageMock.objectExists(cacheObject, "story.html") } returns true
        every { storageMock.getObjectLink(cacheObject, "story.html") } returns objectLink

        // Act
        val result = underTest.getObjectLink(cacheObject, listOf("index.html", "index.htm", "story.html"))

        // Assert
        assert(result == objectLink)

        verifySequence {
            storageMock.objectExists(cacheObject, EduHtmlService.COMPLETION_MARKER_PATH)
            storageMock.objectExists(cacheObject, "index.html")
            storageMock.objectExists(cacheObject, "index.htm")
            storageMock.objectExists(cacheObject, "story.html")
            storageMock.getObjectLink(cacheObject, "story.html")
        }
    }

    @Test
    fun testGetObjectLinkUsesCustomMainEntityOverride() {
        // Arrange
        val objectLink = ObjectLink(link = "customlink")
        val cacheObject = mockk<CacheObject>(relaxed = true)

        every { storageMock.objectExists(cacheObject, EduHtmlService.COMPLETION_MARKER_PATH) } returns true
        every { storageMock.objectExists(cacheObject, "content/start.html") } returns true
        every { storageMock.getObjectLink(cacheObject, "content/start.html") } returns objectLink

        // Act
        val result = underTest.getObjectLink(cacheObject, listOf("content/start.html"))

        // Assert
        assert(result == objectLink)
        verifySequence {
            storageMock.objectExists(cacheObject, EduHtmlService.COMPLETION_MARKER_PATH)
            storageMock.objectExists(cacheObject, "content/start.html")
            storageMock.getObjectLink(cacheObject, "content/start.html")
        }
    }

    @Test
    fun testGetObjectLinkThrowsExceptionIfNoCandidateExists() {
        // Arrange
        val cacheObject = mockk<CacheObject>(relaxed = true)

        every { storageMock.objectExists(cacheObject, EduHtmlService.COMPLETION_MARKER_PATH) } returns true
        every { storageMock.objectExists(cacheObject, not(EduHtmlService.COMPLETION_MARKER_PATH)) } returns false

        // Act and assert
        assertThrows<ResourceNotFoundException> {
            underTest.getObjectLink(cacheObject, listOf("index.html", "index.htm", "story.html"))
        }
        verifySequence {
            storageMock.objectExists(cacheObject, EduHtmlService.COMPLETION_MARKER_PATH)
            storageMock.objectExists(cacheObject, "index.html")
            storageMock.objectExists(cacheObject, "index.htm")
            storageMock.objectExists(cacheObject, "story.html")
        }
    }

    @Test
    fun testGetObjectLinkTreatsRenderingWithoutCompletionMarkerAsNotCached() {
        // A half-extracted archive still has its entry point (index.html sits near the top of the
        // central directory), so without the marker check it would be served forever and the
        // conversion would never be retried.
        val cacheObject = mockk<CacheObject>(relaxed = true)

        every { storageMock.objectExists(cacheObject, EduHtmlService.COMPLETION_MARKER_PATH) } returns false
        every { storageMock.objectExists(cacheObject, "index.html") } returns true

        assertThrows<ResourceNotFoundException> {
            underTest.getObjectLink(cacheObject, listOf("index.html", "index.htm", "story.html"))
        }

        // The entry point is never probed, so no link can be handed out for an incomplete rendering.
        verifySequence {
            storageMock.objectExists(cacheObject, EduHtmlService.COMPLETION_MARKER_PATH)
        }
    }

    @Test
    fun testResolveMainEntityAndEntryCandidates() {
        // Override present (with leading slash to be trimmed)
        every { node.properties } returns mapOf("ccm:ccressourcemainentity" to listOf("/story.html"))
        val mainEntity = underTest.resolveMainEntity(node)
        assert(mainEntity == "story.html")
        assert(underTest.entryCandidates(mainEntity) == listOf("story.html"))

        // No override -> default candidate list
        every { node.properties } returns emptyMap()
        assert(underTest.resolveMainEntity(node) == null)
        assert(underTest.entryCandidates(null) == listOf("index.html", "index.htm", "story.html"))
    }

    @Test
    fun testCreateJobPersistsMainEntityOverrideInAdditionalData() {
        // Arrange
        val dummyJob = jobDataProvider.getJobWithoutSubJobs()
        every { jobRepoMock.findAllByEsObjectId("dummyNodeId") } returns emptyList()
        every { mapperMock.nodeToRenderingJob(node, "EDUHTML", true) } returns dummyJob
        every { jobRepoMock.save(dummyJob) } returns dummyJob
        val subJobSlot = slot<SubJob>()
        every { subJobRepoMock.save(capture(subJobSlot)) } returns mockk<SubJob>()
        justRun { amqpTemplateMock.convertAndSend("exchange", "routingkey", any<RenderingJobMessage>()) }
        every { node.ref.id } returns "dummyNodeId"
        every { node.properties } returns mapOf("ccm:ccressourcemainentity" to listOf("/player.html"))

        // Act
        underTest.createJob(node, "EDUHTML")

        // Assert
        assert(subJobSlot.captured.additionalData == mapOf("mainEntity" to "player.html"))
    }

    @Test
    fun testCreateJobCreatesJobAndReturnsJobIdIfNonRunningJobFound() {
        // Arrange
        val finishedJob = mockk<RenderingJob>()
        val dummyJob = jobDataProvider.getJobWithoutSubJobs()
        every { finishedJob.status } returns RenderingJobStatus.FINISHED
        every { jobRepoMock.findAllByEsObjectId("dummyNodeId") } returns listOf(finishedJob)
        every { mapperMock.nodeToRenderingJob(node, "EDUHTML", true) } returns dummyJob
        every { jobRepoMock.save(dummyJob) } returns dummyJob
        val subJobSlot = slot<SubJob>()
        every { subJobRepoMock.save(capture(subJobSlot)) } returns mockk<SubJob>()
        val message = RenderingJobMessage(id = dummyJob.id.toString())
        justRun { amqpTemplateMock.convertAndSend("exchange", "routingkey", message) }
        every { node.ref.id } returns "dummyNodeId"

        excludeRecords {
            finishedJob.status
        }

        // Act
        val result = underTest.createJob(node, "EDUHTML")

        // Assert
        assert(subJobSlot.captured.routingKey == "routingkey")
        assert(subJobSlot.captured.parent.id.toString() == dummyJob.id.toString())

        assert(result == dummyJob.id.toString())

        verifySequence {
            jobRepoMock.findAllByEsObjectId("dummyNodeId")
            mapperMock.nodeToRenderingJob(node, "EDUHTML", true)
            jobRepoMock.save(dummyJob)
            subJobRepoMock.save(any())
            amqpTemplateMock.convertAndSend("exchange", "routingkey", message)
        }
    }
}
