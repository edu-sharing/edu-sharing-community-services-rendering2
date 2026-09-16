package org.edu_sharing.rendering.modules.h5p

import io.mockk.*
import org.edu_sharing.rendering.config.AppInfo
import org.edu_sharing.rendering.config.H5P_BASE_PATH
import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.renderingJob.MainJobLogic
import org.edu_sharing.rendering.renderingJob.SubJobHeartbeat
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.RenderingJobStatus
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.edu_sharing.rendering.renderingJob.entity.SubJobStatus
import org.edu_sharing.rendering.renderingJob.queue.RenderingJobMessage
import org.edu_sharing.rendering.renderingJob.repository.RenderingJobRepository
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.edu_sharing.rendering.testUtils.JobDataProvider
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test


class H5pImportReceiverTest {

    // Create mocks
    private val mainJobLogic = mockk<MainJobLogic>()
    private val renderingJobRepository = mockk<RenderingJobRepository>()
    private val subJobRepository = mockk<SubJobRepository>()
    private val h5pUploadService = mockk<H5pUploadService>()
    private val jobDataProvider = JobDataProvider()
    private val mapper = Mapper()
    private val appInfo = AppInfo()
    // Real instance (not a mock): run() just executes the block synchronously, and the periodic
    // touch (every 5 min) never fires within a unit test's lifetime, so no stubbing needed.
    private val subJobHeartbeat = SubJobHeartbeat(mockk(relaxed = true))

    // The class under test
    private val underTest = H5pImportReceiver(
        mainJobLogic,
        renderingJobRepository,
        subJobRepository,
        h5pUploadService,
        mapper,
        appInfo,
        subJobHeartbeat
    )

    @BeforeEach
    fun setUp() {
        appInfo.public = AppInfo.ConnectionInfo("http", "localhost", 80, "", "http://localhost:80", )
    }

    @Test
    fun testReceiveMessageSetsSubJobToFinishedIfUploadSucceeds() {

        // Arrange
        val message = RenderingJobMessage("messageId")
        val job = jobDataProvider.getJobWithoutSubJobs("H5P")
        val subJob = jobDataProvider.getDummySubJob(
            subId = JobDataProvider.SUB_ID_1,
            mimeType = "application/zip",
            module = "H5P",
            quality = 0,
            status = SubJobStatus.QUEUED
        )
        job.subJobs.add(subJob)

        val subJobSlot = slot<SubJob>()
        val mainJobSlot = slot<RenderingJob>()
        val statusList = mutableListOf<SubJobStatus>()
        val cacheObjectSlot = slot<CacheObject>()

        every { mainJobLogic.getMainJobEntry(message.id) } returns job
        every { renderingJobRepository.save(capture(mainJobSlot)) } answers {
            Assertions.assertEquals(RenderingJobStatus.PROCESSING, mainJobSlot.captured.status)
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
        val expectedStatusSequence = mutableListOf(SubJobStatus.PROCESSING, SubJobStatus.FINISHED)
        assert(statusList == expectedStatusSequence)

        assert(subJob.message == "http://localhost:80$H5P_BASE_PATH/contentId")

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
    fun testReceiveMessageJustReturnsIfSubJobIsNotQueued() {
        // A redelivered message must not import a second time — the sub-job is the stage marker.
        // Arrange
        val message = RenderingJobMessage("messageId")
        val job = jobDataProvider.getJobWithoutSubJobs("H5P")
        job.subJobs.add(
            jobDataProvider.getDummySubJob(
                subId = JobDataProvider.SUB_ID_1,
                mimeType = "application/zip",
                module = "H5P",
                quality = 0,
                status = SubJobStatus.PROCESSING
            )
        )
        every { mainJobLogic.getMainJobEntry(message.id) } returns job

        // Act
        underTest.receiveMessage(message)

        // Assert
        verify(exactly = 1) { mainJobLogic.getMainJobEntry(message.id) }
        confirmVerified(mainJobLogic, renderingJobRepository, subJobRepository, h5pUploadService)
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
            status = SubJobStatus.QUEUED
        )
        job.subJobs.add(subJob)

        val subJobSlot = slot<SubJob>()
        val mainJobSlot = slot<RenderingJob>()
        val statusList = mutableListOf<SubJobStatus>()
        val cacheObjectSlot = slot<CacheObject>()
        val testMessage = "testMessage"

        every { mainJobLogic.getMainJobEntry(message.id) } returns job
        every { renderingJobRepository.save(capture(mainJobSlot)) } answers {
            Assertions.assertEquals(RenderingJobStatus.PROCESSING, mainJobSlot.captured.status)
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
        val expectedStatusSequence = mutableListOf(SubJobStatus.PROCESSING, SubJobStatus.FAILED)
        assert(statusList == expectedStatusSequence)

        verify(exactly = 1) { mainJobLogic.getMainJobEntry(message.id) }
        verify(exactly = 1) { renderingJobRepository.save(any()) }

        verify(exactly = 2) { subJobRepository.save(any()) }

        verify(exactly = 1) { h5pUploadService.getContentId(any()) }
        verify(exactly = 1) { mainJobLogic.processMainJob(any()) }
        confirmVerified(mainJobLogic, renderingJobRepository, subJobRepository, h5pUploadService)
    }
}
