package org.edu_sharing.rendering.renderingJob

import io.mockk.mockk
import org.edu_sharing.rendering.renderingJob.metrics.RenderingMetrics
import org.edu_sharing.rendering.renderingJob.repository.RenderingJobRepository

class MainJobLogicTest {
    private val jobRepository: RenderingJobRepository = mockk()
    private val renderingMetrics: RenderingMetrics = mockk(relaxed = true)
    private val mainJobLogic = MainJobLogic(jobRepository, renderingMetrics)

    /*@Test
    fun testIfProcessMainJobReturnsFalseIfNoJobFound() {
        // Arrange
        val jobId = "507f191e810c19729de860ea"
        every { jobRepository.findByIdOrNull(ObjectId(jobId)) } returns null
        // Act
        val result = mainJobLogic.processMainJob(jobId)
        // Assert
        verify(exactly = 1) { jobRepository.findByIdOrNull(ObjectId(jobId)) }
        assertTrue(result)
        confirmVerified(jobRepository)
    }

    @Test
    fun testIfProcessMainJobReturnsFalseAndDoesNotModifyJobIfQueuedSubJobsFound() {
        // Arrange
        val jobId = "507f191e810c19729de860ea"
        val job = prepareJobForTesting(jobId, JobStatus.QUEUED)
        every { jobRepository.findByIdOrNull(ObjectId(jobId)) } returns job
        // Act
        val result = mainJobLogic.processMainJob(jobId)
        // Assert
        verify(exactly = 1) { jobRepository.findByIdOrNull(ObjectId(jobId)) }
        assertFalse(result)
        assert(job.status == JobStatus.PROCESSING)
        assert(job.finishedTimestamp == null)
        confirmVerified(jobRepository)
    }

    @Test
    fun testIfProcessMainJobReturnsFalseAndDoesNotModifyJobIfProcessingSubJobsFound() {
        // Arrange
        val jobId = "507f191e810c19729de860ea"
        val job = prepareJobForTesting(jobId, JobStatus.PROCESSING)
        every { jobRepository.findByIdOrNull(ObjectId(jobId)) } returns job
        // Act
        val result = mainJobLogic.processMainJob(jobId)
        // Assert
        verify(exactly = 1) { jobRepository.findByIdOrNull(ObjectId(jobId)) }
        assertFalse(result)
        assert(job.status == JobStatus.PROCESSING)
        assert(job.finishedTimestamp == null)
        confirmVerified(jobRepository)
    }

    @Test
    fun testIfProcessMainJobReturnsReturnsTrueAndModifiesJobCorrectlyIfAllFinished() {
        // Arrange
        val jobId = "507f191e810c19729de860ea"
        val job = prepareJobForTesting(jobId, JobStatus.FINISHED)
        every { jobRepository.findByIdOrNull(ObjectId(jobId)) } returns job
        every { jobRepository.save(any<RenderingJob>()) } returns job
        // Act
        val result = mainJobLogic.processMainJob(jobId)
        // Assert
        verify(exactly = 1) { jobRepository.findByIdOrNull(ObjectId(jobId)) }
        verify(exactly = 1) { jobRepository.save(any<RenderingJob>()) }
        assertTrue(result)
        assert(job.status == JobStatus.FINISHED)
        assert(job.finishedTimestamp != null)
        confirmVerified(jobRepository)
    }

    @Test
    fun testIfProcessMainJobReturnsReturnsTrueAndModifiesJobCorrectlyIfAllFailed() {
        // Arrange
        val jobId = "507f191e810c19729de860ea"
        val job = prepareJobForTesting(jobId, JobStatus.FAILED)
        every { jobRepository.findByIdOrNull(ObjectId(jobId)) } returns job
        every { jobRepository.save(any<RenderingJob>()) } returns job
        // Act
        val result = mainJobLogic.processMainJob(jobId)
        // Assert
        verify(exactly = 1) { jobRepository.findByIdOrNull(ObjectId(jobId)) }
        verify(exactly = 1) { jobRepository.save(any<RenderingJob>()) }
        assertTrue(result)
        assert(job.status == JobStatus.FAILED)
        assert(job.finishedTimestamp != null)
        confirmVerified(jobRepository)
    }

    private fun prepareJobForTesting(id: String, minStatus: JobStatus): RenderingJob {
        val job = RenderingJob(
            id = ObjectId(id),
            esHash = "hash",
            esObjectId = "esobjectid",
            esObjectType = "esobjecttype",
            mimeType = "multipart/form-data",
            module = "VIDEO",
            nodeVersion = "1.2",
            repoId = "repoid",
            status = JobStatus.PROCESSING
        )
        val subJob = SubJob(
            id = ObjectId(),
            parent = job,
            routingKey = "routingkey",
            status = minStatus
        )
        job.subJobs = mutableListOf(subJob)
        return job
    }*/
}