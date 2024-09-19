package org.edu_sharing.rendering.modules

import io.mockk.*
import io.mockk.junit5.MockKExtension
import org.bson.types.ObjectId
import org.edu_sharing.rendering.dto.CacheObject
import org.edu_sharing.rendering.dto.mapper.Mapper
import org.edu_sharing.rendering.renderingJobs.RenderingJobMessage
import org.edu_sharing.rendering.renderingJobs.entity.JobStatus
import org.edu_sharing.rendering.renderingJobs.entity.RenderingJob
import org.edu_sharing.rendering.renderingJobs.MainJobCreationService
import org.edu_sharing.rendering.processing.JobDataProvider
import org.edu_sharing.rendering.renderingJobs.repository.RenderingJobRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.amqp.core.AmqpTemplate

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
    fun testCreateMainJobCreatesAndEnqueuesJob() {
        // Arrange
        val cacheObject = mockk<CacheObject>()
        val module = RenderModules.AUDIO
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
        val module = RenderModules.AUDIO
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
    }
}