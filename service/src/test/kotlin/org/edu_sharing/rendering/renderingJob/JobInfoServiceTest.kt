package org.edu_sharing.rendering.renderingJob

import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.edu_sharing.rendering.modules.ModuleRegistry
import org.edu_sharing.rendering.modules.RenderModule
import org.edu_sharing.rendering.renderingJob.repository.RenderingJobRepository
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.edu_sharing.rendering.testUtils.JobDataProvider
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class JobInfoServiceTest {
    private val jobRepository = mockk<RenderingJobRepository>()
    private val subJobRepository = mockk<SubJobRepository>()
    private val moduleRegistry = mockk<ModuleRegistry>()
    private val jobDataProvider = JobDataProvider()

    private lateinit var underTest: JobInfoService

    @BeforeEach
    fun setup() {
        underTest = JobInfoService(jobRepository, subJobRepository, moduleRegistry)
        clearAllMocks()
    }

    @Test
    fun refreshLinksDelegatesToModuleWhenSupported() {
        val job = jobDataProvider.getJobWithoutSubJobs()
        val renderModule = mockk<RenderModule>()
        every { moduleRegistry.getRenderModule<RenderModule>("HTML") } returns renderModule
        every { renderModule.supportsLinkRefresh() } returns true
        every { renderModule.refreshLinks(job) } just Runs

        underTest.refreshLinks(job)

        verify(exactly = 1) { renderModule.refreshLinks(job) }
    }

    @Test
    fun refreshLinksThrowsWhenModuleDoesNotSupportIt() {
        val job = jobDataProvider.getJobWithoutSubJobs()
        val renderModule = mockk<RenderModule>()
        every { moduleRegistry.getRenderModule<RenderModule>("HTML") } returns renderModule
        every { renderModule.supportsLinkRefresh() } returns false

        assertThrows<IllegalArgumentException> { underTest.refreshLinks(job) }

        verify(exactly = 0) { renderModule.refreshLinks(any()) }
    }

   /* @BeforeEach
    fun setup() {
        underTest = JobInfoService(jobRepository, subJobRepository, moduleRegistry)
        clearAllMocks()
    }

    @Test
    fun testGetRenderingJobReturnsJobIfFound() {
        // Arrange
        val job = jobDataProvider.getJobWithoutSubJobs()
        every { jobRepository.findByIdOrNull(ObjectId(JobDataProvider.DUMMY_JOB_ID)) } returns job

        // Act
        val result = underTest.getRenderingJob(JobDataProvider.DUMMY_JOB_ID)

        // Assert
        assert(result == job)

        verify(exactly = 1) { jobRepository.findByIdOrNull(ObjectId(JobDataProvider.DUMMY_JOB_ID)) }
        confirmVerified(jobRepository, subJobRepository, moduleRegistry)
    }

    @Test
    fun testGetRenderingJobThrowsExceptionIfJobNotFound() {
        // Arrange
        every { jobRepository.findByIdOrNull(ObjectId(JobDataProvider.DUMMY_JOB_ID)) } returns null

        // Act
        assertThrows<EntryNotFoundException> { underTest.getRenderingJob(JobDataProvider.DUMMY_JOB_ID) }

        // Assert
        verify(exactly = 1) { jobRepository.findByIdOrNull(ObjectId(JobDataProvider.DUMMY_JOB_ID)) }
        confirmVerified(jobRepository, subJobRepository, moduleRegistry)
    }

    @Test
    fun testGetJobInfoReturnsInfoForQueuedJobIfFound() {
        // Arrange
        val job = jobDataProvider.getJobWithoutSubJobs()
        job.status = JobStatus.QUEUED

        // Act
        val result = underTest.getJobInfo(job)

        // Assert
        assert(result.jobs.size == 1)
        assert(result.jobs[0].status == JobStatus.QUEUED)
        assert(result.status == JobStatus.QUEUED)
        assert(result.module == "HTML")

        confirmVerified(jobRepository, subJobRepository, moduleRegistry)
    }

    @Test
    fun testGetJobInfoReturnsInfoForProcessingJobIfFound() {
        // Arrange
        val job = jobDataProvider.getJobWithoutSubJobs()
        job.status = JobStatus.PROCESSING

        // Act
        val result = underTest.getJobInfo(job)

        // Assert
        assert(result.jobs.size == 1)
        assert(result.jobs[0].status == JobStatus.PROCESSING)
        assert(result.status == JobStatus.PROCESSING)
        assert(result.module == "HTML")

        confirmVerified(jobRepository, subJobRepository, moduleRegistry)
    }

    @Test
    fun testGetJobInfoReturnsInfoForProcessingJobWithSubJobs() {
        val job = jobDataProvider.getJobWithoutSubJobs()
        job.status = JobStatus.PROCESSING
        val queuedSubJob = jobDataProvider.getDummySubJob(
            subId = JobDataProvider.SUB_ID_1,
            status = JobStatus.QUEUED,
            mimeType = "image/jpeg",
            module = "EDUHTML",
            quality = 1
        )
        queuedSubJob.createdDate = Date()
        val processingSubJob = jobDataProvider.getDummySubJob(
            subId = JobDataProvider.SUB_ID_2,
            status = JobStatus.PROCESSING,
            mimeType = "image/jpeg",
            module = "EDUHTML",
            quality = 2
        )
        processingSubJob.progress = 23
        val finishedSubJob = jobDataProvider.getDummySubJob(
            subId = JobDataProvider.SUB_ID_3,
            status = JobStatus.FINISHED,
            mimeType = "image/jpeg",
            module = "EDUHTML",
            quality = 3
        )
        val failedSubJob = jobDataProvider.getDummySubJob(
            subId = JobDataProvider.SUB_ID_4,
            status = JobStatus.FAILED,
            mimeType = "image/jpeg",
            module = "EDUHTML",
            quality = 4
        )
        job.subJobs = mutableListOf(queuedSubJob, processingSubJob, finishedSubJob, failedSubJob)

        val renderModule = mockk<RenderModule>()

        every { renderModule.getObjectLinkFromJobData(any(), any()) } returns ObjectLink(link = "mylink")
        every { moduleRegistry.getRenderModule<RenderModule>("HTML") } returns renderModule
        every { subJobRepository.getQueuePosition(
            createDate = queuedSubJob.createdDate,
            status = queuedSubJob.status,
            routingKey = queuedSubJob.routingKey,
            priority = queuedSubJob.priority
        ) } returns 12

        // Act
        val result = underTest.getJobInfo(job)

        assert(result.status == JobStatus.PROCESSING)
        assert(result.jobs.size == 4)
        assert(result.module == "HTML")

        val infoForQueued = result.jobs.first { it.status == JobStatus.QUEUED }
        assert(infoForQueued.quality == 1)
        assert(infoForQueued.objectLink == null)
        assert(infoForQueued.progress == 12L)

        val infoForProcessing = result.jobs.first { it.status == JobStatus.PROCESSING }
        assert(infoForProcessing.quality == 2)
        assert(infoForProcessing.objectLink == null)
        assert(infoForProcessing.progress == 23L)

        val infoForFinished = result.jobs.first { it.status == JobStatus.FINISHED }
        assert(infoForFinished.quality == 3)
        assert(infoForFinished.objectLink?.link == "mylink")
        assert(infoForFinished.progress == 0L)

        val infoForFailed = result.jobs.first { it.status == JobStatus.FAILED }
        assert(infoForFailed.quality == 4)
        assert(infoForFailed.objectLink == null)
        assert(infoForFailed.progress == 0L)

        verifySequence {
            moduleRegistry.getRenderModule<RenderModule>("HTML")
            subJobRepository.getQueuePosition(
                createDate = queuedSubJob.createdDate,
                status = queuedSubJob.status,
                routingKey = queuedSubJob.routingKey,
                priority = queuedSubJob.priority
            )
            renderModule.getObjectLinkFromJobData(any(), any())
        }
    }*/
}