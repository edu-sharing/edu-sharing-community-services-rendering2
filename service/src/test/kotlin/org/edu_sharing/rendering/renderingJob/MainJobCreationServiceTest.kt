package org.edu_sharing.rendering.renderingJob

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import org.bson.types.ObjectId
import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.modules.RenderModule
import org.edu_sharing.rendering.renderingJob.repository.RenderingJobRepository
import org.edu_sharing.rendering.testUtils.JobDataProvider
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.amqp.core.AmqpTemplate
import org.springframework.dao.DuplicateKeyException

@ExtendWith(MockKExtension::class)
class MainJobCreationServiceTest {
    private val amqpTemplate = mockk<AmqpTemplate>()
    private val renderingJobRepository = mockk<RenderingJobRepository>()
    private val mapper = mockk<Mapper>()
    private val jobDataProvider = JobDataProvider()

    lateinit var underTest: MainJobCreationService

    @BeforeEach
    fun setup() {
        underTest = MainJobCreationService(
            amqpTemplate, renderingJobRepository, mapper
        )
        underTest.topicExchangeName = "topicExchangeName"
        underTest.jobRoutingKey = "jobRoutingKey"
        clearAllMocks()
    }

    @Test
    fun retrieveOrCreateJobReusesExistingJobWhenConcurrentInsertLosesTheRace() {
        // Arrange: no reusable job on the first lookup, but a concurrent request inserts one, so our
        // save hits the activeJobPerNodeHash unique index (DuplicateKeyException) and we reuse it.
        val cacheObject = mockk<CacheObject>()
        val module = mockk<RenderModule>()
        val existingJob = jobDataProvider.getJobWithoutSubJobs() // PROCESSING, esHash "hash"
        val newJob = jobDataProvider.getJobWithoutSubJobs(id = ObjectId(JobDataProvider.DUMMY_JOB_ID_2))

        every { module.module() } returns "IMAGE"
        every { cacheObject.nodeId } returns "node123"
        every { cacheObject.hash } returns JobDataProvider.HASH
        every { renderingJobRepository.findAllByEsObjectId("node123") } returnsMany
            listOf(emptyList(), listOf(existingJob))
        every { mapper.cacheObjectToRenderingJob(cacheObject, "IMAGE", true) } returns newJob
        every { renderingJobRepository.save(newJob) } throws DuplicateKeyException("duplicate active job")

        // Act
        val result = underTest.retrieveOrCreateJob(cacheObject, module)

        // Assert: the existing job's id is returned, and the loser never publishes a message.
        assert(result == JobDataProvider.DUMMY_JOB_ID)
        verify(exactly = 2) { renderingJobRepository.findAllByEsObjectId("node123") }
        verify(exactly = 1) { renderingJobRepository.save(newJob) }
        verify(exactly = 0) { amqpTemplate.convertAndSend(any<String>(), any<String>(), any<Any>()) }
    }

    /*@Test
    fun testCreateMainJobCreatesAndEnqueuesJob() {
        // Arrange
        val cacheObject = mockk<CacheObject>()
        val module = "AUDIO"
        val renderingJob = mockk<RenderingJob>()
        val missingQualities = listOf(100, 200)
        val jobId = ObjectId()
        val message = RenderingJobMessage(
            id = jobId.toString(),
            missingQualities = missingQualities
        )

        every { mapper.cacheObjectToRenderingJob(cacheObject, module) } returns renderingJob
        every { renderingJobRepository.save(renderingJob) } returns renderingJob
        every { renderingJob.id } returns jobId
        justRun { amqpTemplate.convertAndSend("topicExchangeName", "jobRoutingKey", message) }

        excludeRecords {
            renderingJob.id
        }

        // Act
        val result = underTest.createMainJob(cacheObject, module, missingQualities)

        // Assert
        assert(result == jobId.toString())

        verifySequence {
            mapper.cacheObjectToRenderingJob(cacheObject, module)
            renderingJobRepository.save(renderingJob)
            amqpTemplate.convertAndSend("topicExchangeName", "jobRoutingKey", message)
        }
    }

    @Test
    fun testCreateMainJobCreatesAndEnqueuesJobWithEmptyQualityListAsDefault() {
        // Arrange
        val cacheObject = mockk<CacheObject>()
        val module = "AUDIO"
        val renderingJob = mockk<RenderingJob>()
        val missingQualities = emptyList<Int>()
        val jobId = ObjectId()
        val message = RenderingJobMessage(
            id = jobId.toString(),
            missingQualities = missingQualities
        )

        every { mapper.cacheObjectToRenderingJob(cacheObject, module) } returns renderingJob
        every { renderingJobRepository.save(renderingJob) } returns renderingJob
        every { renderingJob.id } returns jobId
        justRun { amqpTemplate.convertAndSend("topicExchangeName", "jobRoutingKey", message) }

        excludeRecords {
            cacheObject.nodeId
        }

        // Act
        val result = underTest.createMainJob(cacheObject, module)

        // Assert
        assert(result == jobId.toString())

        verifySequence {
            mapper.cacheObjectToRenderingJob(cacheObject, module)
            renderingJobRepository.save(renderingJob)
            renderingJob.id
            amqpTemplate.convertAndSend("topicExchangeName", "jobRoutingKey", message)
            renderingJob.id
        }
    }

    @Test
    fun testGetExistingJobIdReturnsNullIfNoJobFound() {
        // Arrange
        val cacheObject = mockk<CacheObject>()

        every { cacheObject.nodeId } returns "node123"
        every { renderingJobRepository.findAllByEsObjectId("node123") } returns emptyList()

        // Act
        val result = underTest.getExistingJobId(cacheObject)

        // Assert
        assert(result == null)
        verify(exactly = 1) { renderingJobRepository.findAllByEsObjectId("node123") }

    }

    @Test
    fun testGetExistingJobIdReturnsNullIfNoActiveJobFound() {
        // Arrange
        val cacheObject = mockk<CacheObject>()
        val failedJob = jobDataProvider.getJobWithoutSubJobs()
        failedJob.status = JobStatus.FAILED
        val finishedJob = jobDataProvider.getJobWithoutSubJobs(id = ObjectId(JobDataProvider.DUMMY_JOB_ID_2))
        finishedJob.status = JobStatus.FINISHED
        val jobList = listOf(failedJob, finishedJob)

        every { cacheObject.nodeId } returns "node123"
        every { renderingJobRepository.findAllByEsObjectId("node123") } returns jobList

        excludeRecords { cacheObject.nodeId }

        // Act
        val result = underTest.getExistingJobId(cacheObject)

        // Assert
        assert(result == null)
        verify(exactly = 1) { renderingJobRepository.findAllByEsObjectId("node123") }
    }

    @Test
    fun testGetExistingJobIdReturnsNullIfHashDoesNotMatch() {
        // Arrange
        val cacheObject = mockk<CacheObject>()
        val queuedJob = jobDataProvider.getJobWithoutSubJobs()
        queuedJob.status = JobStatus.QUEUED
        val processingJob = jobDataProvider.getJobWithoutSubJobs(id = ObjectId(JobDataProvider.DUMMY_JOB_ID_2))
        processingJob.status = JobStatus.PROCESSING
        val jobList = listOf(queuedJob, processingJob)

        every { cacheObject.nodeId } returns "node123"
        every { renderingJobRepository.findAllByEsObjectId("node123") } returns jobList
        every { cacheObject.hash } returns "thisHashDoesNotMatch"

        excludeRecords {
            cacheObject.nodeId
            cacheObject.hash
        }

        // Act
        val result = underTest.getExistingJobId(cacheObject)

        assert(result == null)
        verify(exactly = 1) { renderingJobRepository.findAllByEsObjectId("node123") }
        confirmVerified(renderingJobRepository)
    }

    @Test
    fun testGetExistingJobIdReturnsRunningJobIfAllMatches() {
        // Arrange
        val cacheObject = mockk<CacheObject>()
        val queuedJob = jobDataProvider.getJobWithoutSubJobs()
        queuedJob.status = JobStatus.QUEUED
        val finishedJob = jobDataProvider.getJobWithoutSubJobs(id = ObjectId(JobDataProvider.DUMMY_JOB_ID_2))
        finishedJob.status = JobStatus.FINISHED
        val jobList = listOf(queuedJob, finishedJob)

        every { cacheObject.nodeId } returns "node123"
        every { renderingJobRepository.findAllByEsObjectId("node123") } returns jobList
        every { cacheObject.hash } returns JobDataProvider.HASH

        excludeRecords {
            cacheObject.nodeId
            cacheObject.hash
        }

        // Act
        val result = underTest.getExistingJobId(cacheObject)

        assert(result == JobDataProvider.DUMMY_JOB_ID)
        verify(exactly = 1) { renderingJobRepository.findAllByEsObjectId("node123") }
        confirmVerified(renderingJobRepository)
    }*/
}