package org.edu_sharing.rendering.modules.h5p

import io.mockk.mockk
import org.edu_sharing.rendering.config.AppInfo
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.renderingJob.MainJobLogic
import org.edu_sharing.rendering.renderingJob.repository.RenderingJobRepository
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.edu_sharing.rendering.testUtils.JobDataProvider
import org.junit.jupiter.api.BeforeEach


class H5PReceiverTest {

    // Create mocks
    private val mainJobLogic = mockk<MainJobLogic>()
    private val renderingJobRepository = mockk<RenderingJobRepository>()
    private val subJobRepository = mockk<SubJobRepository>()
    private val h5pUploadService = mockk<H5pUploadService>()
    private val jobDataProvider = JobDataProvider()
    private val mapper = Mapper()
    private val appInfo = AppInfo()

    // The class under test
    private val underTest = H5pReceiver(
        mainJobLogic,
        renderingJobRepository,
        subJobRepository,
        h5pUploadService,
        mapper,
        appInfo
    )

    @BeforeEach
    fun setUp() {
        appInfo.public = AppInfo.ConnectionInfo("http", "localhost", 80, "", "http://localhost:80", )
    }

    /*@Test
    fun testReceiveMessageSetsSubJobToFinishedIfUploadSucceeds() {

        // Arrange
        val message = RenderingJobMessage("messageId")
        val job = jobDataProvider.getJobWithoutSubJobs("H5P")
        val subJob = jobDataProvider.getDummySubJob(
            subId = JobDataProvider.SUB_ID_1,
            mimeType = "application/zip",
            module = "H5P",
            quality = 0,
            status = JobStatus.QUEUED
        )
        job.subJobs.add(subJob)

        val subJobSlot = slot<SubJob>()
        val mainJobSlot = slot<RenderingJob>()
        val statusList = mutableListOf<JobStatus>()
        val cacheObjectSlot = slot<CacheObject>()

        every { mainJobLogic.getMainJobEntry(message.id) } returns job
        every { renderingJobRepository.save(capture(mainJobSlot)) } answers {
            Assertions.assertEquals(JobStatus.PROCESSING, mainJobSlot.captured.status)
            job
        }
        every { subJobRepository.save(capture(subJobSlot)) } answers {
            statusList.add(subJobSlot.captured.status)
            subJob
        }
        every { h5pUploadService.getContentId(capture(cacheObjectSlot)) } answers {
            Assertions.assertEquals(JobDataProvider.ES_OBJECT_ID, cacheObjectSlot.captured.nodeId)
            Assertions.assertEquals(JobDataProvider.HASH, cacheObjectSlot.captured.hash)
            "contentId"
        }
        every { mainJobLogic.processMainJob(message.id) } returns true

        // Act
        underTest.receiveMessage(message)

        // Assert
        val expectedStatusSequence = mutableListOf(JobStatus.PROCESSING, JobStatus.FINISHED)
        assert(statusList == expectedStatusSequence)

        assert(subJob.message == "http://localhost$H5P_BASE_PATH/contentId")

        verify(exactly = 1) { mainJobLogic.getMainJobEntry(message.id) }
        verify(exactly = 1) { renderingJobRepository.save(any()) }

        verify(exactly = 2) { subJobRepository.save(any()) }

        verify(exactly = 1) { h5pUploadService.getContentId(any()) }
        verify(exactly = 1) { mainJobLogic.processMainJob(any()) }
        confirmVerified(mainJobLogic, renderingJobRepository, subJobRepository, h5pUploadService)
    }

    @Test
    fun testReceiveMessageJustReturnsIfNoMainJobFound() {
        // Arrange
        val message = RenderingJobMessage("messageId")
        every { mainJobLogic.getMainJobEntry(message.id) } returns null

        // Act
        underTest.receiveMessage(message)

        // Assert
        verify(exactly = 1) { mainJobLogic.getMainJobEntry(message.id) }
        confirmVerified(mainJobLogic)
    }

    @Test
    fun testReceiveMessageJustReturnsIfMainJobHasNoSubJobs() {
        // Arrange
        val message = RenderingJobMessage("messageId")
        val mainJob = jobDataProvider.getJobWithoutSubJobs("H5P")
        every { mainJobLogic.getMainJobEntry(message.id) } returns mainJob

        // Act
        underTest.receiveMessage(message)

        // Assert
        verify(exactly = 1) { mainJobLogic.getMainJobEntry(message.id) }
        confirmVerified(mainJobLogic)
    }

    @Test
    fun testReceiveMessageSetsSubJobToFailedIfUploadFails() {
        // Arrange
        val message = RenderingJobMessage("messageId")
        val job = jobDataProvider.getJobWithoutSubJobs("H5P")
        val subJob = jobDataProvider.getDummySubJob(
            subId = JobDataProvider.SUB_ID_1,
            mimeType = "application/zip",
            module = "H5P",
            quality = 0,
            status = JobStatus.QUEUED
        )
        job.subJobs.add(subJob)

        val subJobSlot = slot<SubJob>()
        val mainJobSlot = slot<RenderingJob>()
        val statusList = mutableListOf<JobStatus>()
        val cacheObjectSlot = slot<CacheObject>()
        val testMessage = "testMessage"

        every { mainJobLogic.getMainJobEntry(message.id) } returns job
        every { renderingJobRepository.save(capture(mainJobSlot)) } answers {
            Assertions.assertEquals(JobStatus.PROCESSING, mainJobSlot.captured.status)
            job
        }
        every { subJobRepository.save(capture(subJobSlot)) } answers {
            statusList.add(subJobSlot.captured.status)
            subJob
        }
        every { h5pUploadService.getContentId(capture(cacheObjectSlot)) } answers {
            Assertions.assertEquals(JobDataProvider.ES_OBJECT_ID, cacheObjectSlot.captured.nodeId)
            Assertions.assertEquals(JobDataProvider.HASH, cacheObjectSlot.captured.hash)
            throw Exception(testMessage)
        }
        every { mainJobLogic.processMainJob(message.id) } returns true

        // Act
        underTest.receiveMessage(message)

        // Assert
        val expectedStatusSequence = mutableListOf(JobStatus.PROCESSING, JobStatus.FAILED)
        assert(statusList == expectedStatusSequence)

        assert(subJob.message != null && subJob.message!!.contains(testMessage))

        verify(exactly = 1) { mainJobLogic.getMainJobEntry(message.id) }
        verify(exactly = 1) { renderingJobRepository.save(any()) }

        verify(exactly = 2) { subJobRepository.save(any()) }

        verify(exactly = 1) { h5pUploadService.getContentId(any()) }
        verify(exactly = 1) { mainJobLogic.processMainJob(any()) }
        confirmVerified(mainJobLogic, renderingJobRepository, subJobRepository, h5pUploadService)
    }*/
}
