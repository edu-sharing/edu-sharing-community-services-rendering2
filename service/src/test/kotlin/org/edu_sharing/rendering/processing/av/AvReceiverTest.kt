package org.edu_sharing.rendering.processing.av

import io.mockk.*
import org.edu_sharing.rendering.storage.StorageService
import org.edu_sharing.rendering.modules.RenderModules
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.renderingJob.SubJobMessage
import org.edu_sharing.rendering.renderingJob.entity.JobStatus
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.edu_sharing.rendering.modules.av.audio.AudioConversionService
import org.edu_sharing.rendering.modules.av.video.VideoConversionService
import org.edu_sharing.rendering.processing.JobDataProvider
import org.edu_sharing.rendering.processing.MainJobLogic
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.junit.jupiter.api.Test

class AvReceiverTest {
    private val mainJobLogic: MainJobLogic = mockk()
    private val subJobRepository: SubJobRepository = mockk()
    private val audioConversionService: AudioConversionService = mockk()
    private val videoConversionService: VideoConversionService = mockk()
    private val mapper = Mapper()
    private val storageService: StorageService = mockk()
    private val underTest = AvReceiver(
        mainJobLogic = mainJobLogic,
        subJobRepository = subJobRepository,
        audioConversionService = audioConversionService,
        videoConversionService = videoConversionService,
        mapper = mapper,
        storageImplementation = storageService
    )
    private val jobDataProvider = JobDataProvider()

    @Test
    fun testReceiveMessageReturnsEarlyIfNoJobFound() {
        // Arrange
        val message = SubJobMessage(
            id = JobDataProvider.DUMMY_JOB_ID
        )
        every { mainJobLogic.getMainJobEntry(JobDataProvider.DUMMY_JOB_ID) } returns null

        // Act
        underTest.receiveMessage(message)

        // Assert
        verify(exactly = 1) { mainJobLogic.getMainJobEntry(JobDataProvider.DUMMY_JOB_ID) }
        confirmVerified(mainJobLogic)
    }

    @Test
    fun testReceiveMessageReturnsEarlyIfNoSubJobsFound() {
        // Arrange
        val message = SubJobMessage(
            id = JobDataProvider.DUMMY_JOB_ID,
        )
        val emptyJob = jobDataProvider.getJobWithoutSubJobs()
        every { mainJobLogic.getMainJobEntry(JobDataProvider.DUMMY_JOB_ID) } returns emptyJob
        every { mainJobLogic.processMainJob(JobDataProvider.DUMMY_JOB_ID) } returns true

        // Act
        underTest.receiveMessage(message)

        // Assert
        verifyOrder {
            mainJobLogic.getMainJobEntry(JobDataProvider.DUMMY_JOB_ID)
            mainJobLogic.processMainJob(JobDataProvider.DUMMY_JOB_ID)
        }
        confirmVerified(mainJobLogic)
    }

    @Test
    fun testReceiveMessageReturnsEarlyIfMatchingSubJobNotInMainJob() {
        // Arrange
        val message = SubJobMessage(
            id = JobDataProvider.DUMMY_JOB_ID,
            quality = 720
        )
        val job = jobDataProvider.getJobWithoutSubJobs()
        val notMatchingSubJob = jobDataProvider.getDummySubJob(
            subId = JobDataProvider.SUB_ID_2,
            mimeType = "video/mp4",
            module = RenderModules.VIDEO,
            quality = 1080,
            status = JobStatus.QUEUED
        )
        job.subJobs = mutableListOf(notMatchingSubJob)
        every { mainJobLogic.getMainJobEntry(JobDataProvider.DUMMY_JOB_ID) } returns job
        every { mainJobLogic.processMainJob(JobDataProvider.DUMMY_JOB_ID) } returns true

        // Act
        underTest.receiveMessage(message)

        // Assert
        verifyOrder {
            mainJobLogic.getMainJobEntry(JobDataProvider.DUMMY_JOB_ID)
            mainJobLogic.processMainJob(JobDataProvider.DUMMY_JOB_ID)
        }
        confirmVerified(mainJobLogic)


    }

    @Test
    fun testReceiveMessageSetsSubJobToFailedIfConversionFails() {
        //Arrange
        val message = SubJobMessage(
            id = JobDataProvider.DUMMY_JOB_ID,
            quality = 720
        )
        val job = jobDataProvider.getJobWithoutSubJobs(RenderModules.VIDEO)
        val matchingSubJob = jobDataProvider.getDummySubJob(
            subId = JobDataProvider.SUB_ID_1,
            mimeType = "video/mp4",
            module = RenderModules.VIDEO,
            quality = 720,
            status = JobStatus.QUEUED
        )
        val notMatchingSubJob = jobDataProvider.getDummySubJob(
            subId = JobDataProvider.SUB_ID_2,
            mimeType = "video/mp4",
            module = RenderModules.VIDEO,
            quality = 1080,
            status = JobStatus.QUEUED
        )
        job.subJobs = mutableListOf(matchingSubJob, notMatchingSubJob)
        val cacheObject = mapper.renderingJobToCacheObject(job)

        every { mainJobLogic.getMainJobEntry(JobDataProvider.DUMMY_JOB_ID) } returns job
        val subJobSlot = slot<SubJob>()
        val statusList = mutableListOf<JobStatus>()
        val qualityList = mutableListOf<Int>()
        every { subJobRepository.save(capture(subJobSlot)) } answers {
            statusList.add(subJobSlot.captured.status)
            qualityList.add(subJobSlot.captured.quality)

            assert(subJobSlot.captured.id.toString() != JobDataProvider.SUB_ID_2)

            matchingSubJob
        }
        every {
            videoConversionService.convert(
                cacheObject,
                any()
            )
        } throws Exception()
        every { mainJobLogic.processMainJob(JobDataProvider.DUMMY_JOB_ID) } returns false

        //Act
        underTest.receiveMessage(message)

        //Assert
        assert(statusList.size == 2)
        assert(statusList[0] == JobStatus.PROCESSING)
        assert(statusList[1] == JobStatus.FAILED)
        assert(qualityList[0] == 720)
        assert(qualityList[1] == 720)
        verify(exactly = 1) { mainJobLogic.getMainJobEntry(JobDataProvider.DUMMY_JOB_ID) }
        verify(exactly = 2) { subJobRepository.save(any()) }
        verify(exactly = 1) {
            videoConversionService.convert(
                any(),
                any()
            )
        }
        verify(exactly = 1) { mainJobLogic.processMainJob(JobDataProvider.DUMMY_JOB_ID) }
        confirmVerified(mainJobLogic, subJobRepository, videoConversionService)
    }

    @Test
    fun testReceiveMessageSetsSubJobToFinishedIfConversionSucceeds() {
        //Arrange
        val message = SubJobMessage(
            id = JobDataProvider.DUMMY_JOB_ID,
            quality = 720
        )
        val job = jobDataProvider.getJobWithoutSubJobs(RenderModules.VIDEO)
        val matchingSubJob = jobDataProvider.getDummySubJob(
            subId = JobDataProvider.SUB_ID_1,
            mimeType = "video/mp4",
            module = RenderModules.VIDEO,
            quality = 720,
            status = JobStatus.QUEUED
        )
        val notMatchingSubJob = jobDataProvider.getDummySubJob(
            subId = JobDataProvider.SUB_ID_2,
            mimeType = "video/mp4",
            module = RenderModules.VIDEO,
            quality = 1080,
            status = JobStatus.QUEUED
        )
        job.subJobs = mutableListOf(matchingSubJob, notMatchingSubJob)
        val cacheObject = mapper.renderingJobToCacheObject(job)

        every { mainJobLogic.getMainJobEntry(JobDataProvider.DUMMY_JOB_ID) } returns job
        val subJobSlot = slot<SubJob>()
        val statusList = mutableListOf<JobStatus>()
        val subIdSet = mutableSetOf<String>()
        every { subJobRepository.save(capture(subJobSlot)) } answers {
            statusList.add(subJobSlot.captured.status)
            subIdSet.add(subJobSlot.captured.id.toString())

            matchingSubJob
        }
        justRun { videoConversionService.convert(cacheObject, matchingSubJob) }
        every { mainJobLogic.processMainJob(JobDataProvider.DUMMY_JOB_ID) } returns false

        //Act
        underTest.receiveMessage(message)

        //Assert
        val expectedStatusSequence = mutableListOf(JobStatus.PROCESSING, JobStatus.FINISHED)

        assert(statusList == expectedStatusSequence)
        assert(subIdSet.size == 1)
        verify(exactly = 1) { mainJobLogic.getMainJobEntry(JobDataProvider.DUMMY_JOB_ID) }
        verify(exactly = 2) { subJobRepository.save(any()) }
        verify(exactly = 1) {
            videoConversionService.convert(
                cacheObject,
                matchingSubJob
            )
        }
        verify(exactly = 1) { mainJobLogic.processMainJob(JobDataProvider.DUMMY_JOB_ID) }
        confirmVerified(mainJobLogic, subJobRepository, videoConversionService)
    }

    @Test
    fun testReceiveMessageRemovesTempObjectFromBucketIfProcessMainJobReturnsTrue() {
        //Arrange
        val message = SubJobMessage(
            id = JobDataProvider.DUMMY_JOB_ID,
            quality = 0
        )
        val job = jobDataProvider.getJobWithoutSubJobs(RenderModules.AUDIO)
        val matchingSubJob = jobDataProvider.getDummySubJob(
            subId = JobDataProvider.SUB_ID_1,
            mimeType = "audio/ogg",
            module = RenderModules.AUDIO,
            quality = 0,
            status = JobStatus.QUEUED
        )
        job.subJobs = mutableListOf(matchingSubJob)
        val cacheObject = mapper.renderingJobToCacheObject(job)

        every { mainJobLogic.getMainJobEntry(any()) } returns job
        every { subJobRepository.save(any()) } returns matchingSubJob
        justRun { audioConversionService.convert(any(), any()) }
        every { mainJobLogic.processMainJob(any()) } returns true
        justRun { storageService.removeObject(cacheObject, true) }

        //Act
        underTest.receiveMessage(message)

        //Assert
        verify(exactly = 1) { mainJobLogic.getMainJobEntry(any()) }
        verify(exactly = 2) { subJobRepository.save(any()) }
        verify(exactly = 1) { audioConversionService.convert(any(), any()) }
        verify(exactly = 1) { mainJobLogic.processMainJob(any()) }
        verify(exactly = 1) { storageService.removeObject(cacheObject, true) }
        confirmVerified(mainJobLogic, subJobRepository, audioConversionService, storageService)
    }
}