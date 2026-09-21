package org.edu_sharing.rendering.modules.moodle

import io.mockk.mockk
import org.edu_sharing.rendering.modules.ModuleRegistry
import org.edu_sharing.rendering.renderingJob.MainJobLogic
import org.edu_sharing.rendering.renderingJob.SubJobHeartbeat
import org.edu_sharing.rendering.renderingJob.repository.RenderingJobRepository
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.edu_sharing.rendering.testUtils.JobDataProvider

class MoodleReceiverTest {
    private val moodleUploadService: MoodleUploadService = mockk()
    private val renderingJobRepository: RenderingJobRepository = mockk()
    private val subJobRepository: SubJobRepository = mockk()
    private val mainJobLogic: MainJobLogic = mockk()
    private val moduleRegistry: ModuleRegistry = mockk()
    // Real instance (not a mock): run() just executes the block synchronously, and the periodic
    // touch (every 5 min) never fires within a unit test's lifetime, so no stubbing needed.
    private val subJobHeartbeat = SubJobHeartbeat(mockk(relaxed = true))
    private val underTest = MoodleReceiver(
        moodleService = moodleUploadService,
        renderingJobRepository = renderingJobRepository,
        subJobRepository = subJobRepository,
        mainJobLogic = mainJobLogic,
        moduleRegistry = moduleRegistry,
        subJobHeartbeat = subJobHeartbeat
    )
    private val jobDataProvider = JobDataProvider()
    private val module: MoodleRenderModule = mockk()

    /*@Test
    fun testReceiveMessageReturnsEarlyIfNoJobFound() {
        // Arrange
        val message = MoodleJobMessage(
            id = JobDataProvider.DUMMY_JOB_ID,
            authorityName = "authority",
            nodeId = "nodeId",
            title = "title"
        )
        every { mainJobLogic.getMainJobEntry(message.id) } returns null

        // Act
        underTest.receiveMessage(message)

        // Assert
        verify(exactly = 1) { mainJobLogic.getMainJobEntry(message.id) }
        confirmVerified(mainJobLogic)
    }

    @Test
    fun testReceiveMessageReturnsEarlyIfJobHasNoSubJobs() {
        // Arrange
        val message = MoodleJobMessage(
            id = JobDataProvider.DUMMY_JOB_ID,
            authorityName = "authority",
            nodeId = "nodeId",
            title = "title"
        )
        val job = jobDataProvider.getJobWithoutSubJobs()
        every { mainJobLogic.getMainJobEntry(message.id) } returns job

        // Act
        underTest.receiveMessage(message)

        // Assert
        verify(exactly = 1) { mainJobLogic.getMainJobEntry(message.id) }
        confirmVerified(mainJobLogic)
    }

    @Test
    fun testReceiveMessageSetsJobStatusToFailedIfMoodleUploadFails() {
        // Arrange
        val message = MoodleJobMessage(
            id = JobDataProvider.DUMMY_JOB_ID,
            authorityName = "authority",
            nodeId = "nodeId",
            title = "title"
        )
        val job = jobDataProvider.getJobWithoutSubJobs()
        job.module = "MOODLE"
        val subJob = jobDataProvider.getDummySubJob(
            subId = JobDataProvider.SUB_ID_1,
            mimeType = "application/x-zip",
            module = "MOODLE",
            status = JobStatus.QUEUED
        )
        job.subJobs = mutableListOf(subJob)

        val jobSlot = slot<RenderingJob>()
        every { mainJobLogic.getMainJobEntry(message.id) } returns job
        every { renderingJobRepository.save(capture(jobSlot)) } answers {
            // Assert that the proper job with the proper status is processed
            assert(jobSlot.captured.status == JobStatus.PROCESSING)
            assert(jobSlot.captured.id.toString() == JobDataProvider.DUMMY_JOB_ID)

            job
        }
        val subJobSlot = slot<SubJob>()
        val statusList = mutableListOf<JobStatus>()
        every { subJobRepository.save(capture(subJobSlot)) } answers {
            // Assert that the proper job is processed and capture status to list for later assertion
            statusList.add(subJobSlot.captured.status)
            assert(subJobSlot.captured.id.toString() == JobDataProvider.SUB_ID_1)

            subJob
        }
        every { moduleRegistry.getRenderModule<RenderModule>(moduleName = "MOODLE") } returns module
        every { moodleUploadService.getUrl(message, module, job.repoId) } throws Exception()
        every { mainJobLogic.processMainJob(message.id) } returns true

        // Act
        underTest.receiveMessage(message)

        // Assert
        val expectedStatusSequence = mutableListOf(JobStatus.PROCESSING, JobStatus.FAILED)

        assert(statusList == expectedStatusSequence)
        verify(exactly = 1) { mainJobLogic.getMainJobEntry(message.id) }
        verify(exactly = 1) { renderingJobRepository.save(any()) }
        verify(exactly = 2) { subJobRepository.save(any()) }
        verify(exactly = 1) { moduleRegistry.getRenderModule<RenderModule>(moduleName = "MOODLE") }
        verify(exactly = 1) { moodleUploadService.getUrl(message, module, job.repoId) }
        verify(exactly = 1) { mainJobLogic.processMainJob(message.id) }
        confirmVerified(mainJobLogic, renderingJobRepository, subJobRepository, moduleRegistry, moodleUploadService)
    }

    @Test
    fun testReceiveMessageSetsJobStatusToSuccessIfMoodleUploadSucceeds() {
        // Arrange
        val message = MoodleJobMessage(
            id = JobDataProvider.DUMMY_JOB_ID,
            authorityName = "authority",
            nodeId = "nodeId",
            title = "title"
        )
        val job = jobDataProvider.getJobWithoutSubJobs()
        job.module = "MOODLE"
        val subJob = jobDataProvider.getDummySubJob(
            subId = JobDataProvider.SUB_ID_1,
            mimeType = "application/x-zip",
            module = "MOODLE",
            status = JobStatus.QUEUED
        )
        job.subJobs = mutableListOf(subJob)

        val jobSlot = slot<RenderingJob>()
        every { mainJobLogic.getMainJobEntry(message.id) } returns job
        every { renderingJobRepository.save(capture(jobSlot)) } answers {
            // Assert that the proper job with the proper status is processed
            assert(jobSlot.captured.status == JobStatus.PROCESSING)
            assert(jobSlot.captured.id.toString() == JobDataProvider.DUMMY_JOB_ID)

            job
        }
        val subJobSlot = slot<SubJob>()
        val statusList = mutableListOf<JobStatus>()
        every { subJobRepository.save(capture(subJobSlot)) } answers {
            // Assert that the proper job is processed and capture status to list for later assertion
            statusList.add(subJobSlot.captured.status)
            assert(subJobSlot.captured.id.toString() == JobDataProvider.SUB_ID_1)

            subJob
        }
        every { moduleRegistry.getRenderModule<RenderModule>(moduleName = "MOODLE") } returns module
        every { moodleUploadService.getUrl(message, module, job.repoId) } returns "myUrl"
        every { mainJobLogic.processMainJob(message.id) } returns true

        // Act
        underTest.receiveMessage(message)

        // Assert
        val expectedStatusSequence = mutableListOf(JobStatus.PROCESSING, JobStatus.FINISHED)

        assert(statusList == expectedStatusSequence)
        assert(subJob.message == "myUrl")
        verify(exactly = 1) { mainJobLogic.getMainJobEntry(message.id) }
        verify(exactly = 1) { renderingJobRepository.save(any()) }
        verify(exactly = 2) { subJobRepository.save(any()) }
        verify(exactly = 1) { moduleRegistry.getRenderModule<RenderModule>(moduleName = "MOODLE") }
        verify(exactly = 1) { moodleUploadService.getUrl(message, module, job.repoId) }
        verify(exactly = 1) { mainJobLogic.processMainJob(message.id) }
        confirmVerified(mainJobLogic, renderingJobRepository, subJobRepository, moduleRegistry, moodleUploadService)
    }*/
}