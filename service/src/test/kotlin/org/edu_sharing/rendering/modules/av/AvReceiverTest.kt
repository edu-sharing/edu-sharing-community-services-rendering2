package org.edu_sharing.rendering.modules.av

/*
class AvReceiverTest {
    private val mainJobLogic: MainJobLogic = mockk()
    private val subJobRepository: SubJobRepository = mockk()
    private val audioConversionService: AudioConversionService = mockk()
    private val videoConversionService: VideoConversionService = mockk()
    private val mapper = mockk<Mapper>()
    private val storageService: StorageService = mockk()
    private val audioModule: AudioRenderModule = mockk()
    private val videoModule: VideoRenderModule = mockk()
    private val underTest = AvReceiver(
        mainJobLogic = mainJobLogic,
        subJobRepository = subJobRepository,
        audioConversionService = audioConversionService,
        videoConversionService = videoConversionService,
        mapper = mapper,
        storageImplementation = storageService,
        audioModule = audioModule,
        videoModule = videoModule
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
            id = JobDataProvider.DUMMY_JOB_ID, quality = 720
        )
        val job = jobDataProvider.getJobWithoutSubJobs()
        val notMatchingSubJob = jobDataProvider.getDummySubJob(
            subId = JobDataProvider.SUB_ID_2,
            mimeType = "video/mp4",
            module = "VIDEO",
            quality = 1080,
            status = SubJobStatus.QUEUED
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
            id = JobDataProvider.DUMMY_JOB_ID, quality = 720
        )
        val job = jobDataProvider.getJobWithoutSubJobs("VIDEO")
        val matchingSubJob = jobDataProvider.getDummySubJob(
            subId = JobDataProvider.SUB_ID_1,
            mimeType = "video/mp4",
            module = "VIDEO",
            quality = 720,
            status = SubJobStatus.QUEUED
        )
        val notMatchingSubJob = jobDataProvider.getDummySubJob(
            subId = JobDataProvider.SUB_ID_2,
            mimeType = "video/mp4",
            module = "VIDEO",
            quality = 1080,
            status = SubJobStatus.QUEUED
        )
        job.subJobs = mutableListOf(matchingSubJob, notMatchingSubJob)
        val cacheObject = mockk<CacheObject>()

        every { mainJobLogic.getMainJobEntry(JobDataProvider.DUMMY_JOB_ID) } returns job
        every { mapper.renderingJobToCacheObject(job) } returns cacheObject
        every { cacheObject.deepCopy() } returns cacheObject
        val subJobSlot = slot<SubJob>()
        val statusList = mutableListOf<SubJobStatus>()
        val qualityList = mutableListOf<Int>()
        every { subJobRepository.save(capture(subJobSlot)) } answers {
            statusList.add(subJobSlot.captured.status)
            qualityList.add(subJobSlot.captured.quality)

            assert(subJobSlot.captured.id.toString() != JobDataProvider.SUB_ID_2)

            matchingSubJob
        }
        every {
            videoConversionService.convert(
                cacheObject, any()
            )
        } throws Exception()
        every { mainJobLogic.processMainJob(JobDataProvider.DUMMY_JOB_ID) } returns false
        every { audioModule.module() } returns "AUDIO"
        every { videoModule.module() } returns "VIDEO"
        every { subJobRepository.findByIdOrNull(ObjectId(JobDataProvider.SUB_ID_1)) } returns matchingSubJob

        //Act
        underTest.receiveMessage(message)

        //Assert
        assert(statusList.size == 2)
        assert(statusList[0] == SubJobStatus.PROCESSING)
        assert(statusList[1] == SubJobStatus.FAILED)
        assert(qualityList.size == 2)
        assert(qualityList.none { it != 720 })

        verifySequence {
            mainJobLogic.getMainJobEntry(JobDataProvider.DUMMY_JOB_ID)
            mapper.renderingJobToCacheObject(job)
            subJobRepository.save(any())
            audioModule.module()
            videoModule.module()
            cacheObject.deepCopy()
            videoConversionService.convert(any(), any())
            subJobRepository.findByIdOrNull(ObjectId(JobDataProvider.SUB_ID_1))
            subJobRepository.save(any())
            mainJobLogic.processMainJob(JobDataProvider.DUMMY_JOB_ID)
        }
    }

    @Test
    fun testReceiveMessageSetsSubJobToFinishedIfConversionSucceeds() {
        //Arrange
        val message = SubJobMessage(
            id = JobDataProvider.DUMMY_JOB_ID, quality = 720
        )
        val job = jobDataProvider.getJobWithoutSubJobs("VIDEO")
        val matchingSubJob = jobDataProvider.getDummySubJob(
            subId = JobDataProvider.SUB_ID_1,
            mimeType = "video/mp4",
            module = "VIDEO",
            quality = 720,
            status = SubJobStatus.QUEUED
        )
        val notMatchingSubJob = jobDataProvider.getDummySubJob(
            subId = JobDataProvider.SUB_ID_2,
            mimeType = "video/mp4",
            module = "VIDEO",
            quality = 1080,
            status = SubJobStatus.QUEUED
        )
        job.subJobs = mutableListOf(matchingSubJob, notMatchingSubJob)
        val cacheObject = mockk<CacheObject>()
        every { mainJobLogic.getMainJobEntry(JobDataProvider.DUMMY_JOB_ID) } returns job
        every { mapper.renderingJobToCacheObject(job) } returns cacheObject
        every { cacheObject.deepCopy() } returns cacheObject

        val subJobSlot = slot<SubJob>()
        val statusList = mutableListOf<SubJobStatus>()
        val subIdSet = mutableSetOf<String>()
        every { subJobRepository.save(capture(subJobSlot)) } answers {
            statusList.add(subJobSlot.captured.status)
            subIdSet.add(subJobSlot.captured.id.toString())

            matchingSubJob
        }
        justRun { videoConversionService.convert(cacheObject, matchingSubJob) }
        every { mainJobLogic.processMainJob(JobDataProvider.DUMMY_JOB_ID) } returns false
        every { audioModule.module() } returns "AUDIO"
        every { videoModule.module() } returns "VIDEO"
        every { subJobRepository.findByIdOrNull(ObjectId(JobDataProvider.SUB_ID_1)) } returns matchingSubJob


        //Act
        underTest.receiveMessage(message)

        //Assert
        val expectedStatusSequence = mutableListOf(SubJobStatus.PROCESSING, SubJobStatus.FINISHED)

        assert(statusList == expectedStatusSequence)
        assert(subIdSet.size == 1)

        verifySequence {
            mainJobLogic.getMainJobEntry(JobDataProvider.DUMMY_JOB_ID)
            mapper.renderingJobToCacheObject(job)
            subJobRepository.save(any())
            audioModule.module()
            videoModule.module()
            cacheObject.deepCopy()
            videoConversionService.convert(any(), any())
            subJobRepository.findByIdOrNull(ObjectId(JobDataProvider.SUB_ID_1))
            subJobRepository.save(any())
            mainJobLogic.processMainJob(JobDataProvider.DUMMY_JOB_ID)
        }
    }

    @Test
    fun testReceiveMessageRemovesTempObjectFromBucketIfProcessMainJobReturnsTrue() {
        //Arrange
        val message = SubJobMessage(
            id = JobDataProvider.DUMMY_JOB_ID, quality = 720
        )
        val job = jobDataProvider.getJobWithoutSubJobs("VIDEO")
        val matchingSubJob = jobDataProvider.getDummySubJob(
            subId = JobDataProvider.SUB_ID_1,
            mimeType = "video/mp4",
            module = "VIDEO",
            quality = 720,
            status = SubJobStatus.QUEUED
        )
        val notMatchingSubJob = jobDataProvider.getDummySubJob(
            subId = JobDataProvider.SUB_ID_2,
            mimeType = "video/mp4",
            module = "VIDEO",
            quality = 1080,
            status = SubJobStatus.QUEUED
        )
        job.subJobs = mutableListOf(matchingSubJob, notMatchingSubJob)
        val cacheObject = mockk<CacheObject>()
        every { mainJobLogic.getMainJobEntry(JobDataProvider.DUMMY_JOB_ID) } returns job
        every { mapper.renderingJobToCacheObject(job) } returns cacheObject
        every { cacheObject.deepCopy() } returns cacheObject

        val subJobSlot = slot<SubJob>()
        val statusList = mutableListOf<SubJobStatus>()
        val subIdSet = mutableSetOf<String>()
        every { subJobRepository.save(capture(subJobSlot)) } answers {
            statusList.add(subJobSlot.captured.status)
            subIdSet.add(subJobSlot.captured.id.toString())

            matchingSubJob
        }
        justRun { videoConversionService.convert(cacheObject, matchingSubJob) }
        every { mainJobLogic.processMainJob(JobDataProvider.DUMMY_JOB_ID) } returns true
        every { audioModule.module() } returns "AUDIO"
        every { videoModule.module() } returns "VIDEO"
        every { subJobRepository.findByIdOrNull(ObjectId(JobDataProvider.SUB_ID_1)) } returns matchingSubJob
        justRun { storageService.removeObject(cacheObject, true) }

        //Act
        underTest.receiveMessage(message)

        //Assert
        val expectedStatusSequence = mutableListOf(SubJobStatus.PROCESSING, SubJobStatus.FINISHED)

        assert(statusList == expectedStatusSequence)
        assert(subIdSet.size == 1)

        verifySequence {
            mainJobLogic.getMainJobEntry(JobDataProvider.DUMMY_JOB_ID)
            mapper.renderingJobToCacheObject(job)
            subJobRepository.save(any())
            audioModule.module()
            videoModule.module()
            cacheObject.deepCopy()
            videoConversionService.convert(any(), any())
            subJobRepository.findByIdOrNull(ObjectId(JobDataProvider.SUB_ID_1))
            subJobRepository.save(any())
            mainJobLogic.processMainJob(JobDataProvider.DUMMY_JOB_ID)
            storageService.removeObject(cacheObject, true)
        }
    }

    @Test
    fun testReceiveMessageSetsJobToFailedForUnsupportedModule() {
        //Arrange
        val message = SubJobMessage(
            id = JobDataProvider.DUMMY_JOB_ID,
            quality = 720
        )
        val job = jobDataProvider.getJobWithoutSubJobs("NONSENSE")
        val matchingSubJob = jobDataProvider.getDummySubJob(
            subId = JobDataProvider.SUB_ID_1,
            mimeType = "video/mp4",
            module = "NONSENSE",
            quality = 720,
            status = SubJobStatus.QUEUED
        )
        job.subJobs = mutableListOf(matchingSubJob)
        val cacheObject = mockk<CacheObject>()
        every { mainJobLogic.getMainJobEntry(JobDataProvider.DUMMY_JOB_ID) } returns job
        every { mapper.renderingJobToCacheObject(job) } returns cacheObject
        every { cacheObject.deepCopy() } returns cacheObject

        val subJobSlot = slot<SubJob>()
        val statusList = mutableListOf<SubJobStatus>()
        val messageList = mutableListOf<String>()
        every { subJobRepository.save(capture(subJobSlot)) } answers {
            statusList.add(subJobSlot.captured.status)
            messageList.add(subJobSlot.captured.message ?: "")

            matchingSubJob
        }
        every { audioModule.module() } returns "AUDIO"
        every { videoModule.module() } returns "VIDEO"
        every { subJobRepository.findByIdOrNull(ObjectId(JobDataProvider.SUB_ID_1)) } returns matchingSubJob
        every { mainJobLogic.processMainJob(JobDataProvider.DUMMY_JOB_ID) } returns false

        //Act
        underTest.receiveMessage(message)

        // Assert
        assert(statusList.size == 2)
        assert(statusList[0] == SubJobStatus.PROCESSING)
        assert(statusList[1] == SubJobStatus.FAILED)
/**
        assert(messageList.size == 3)
        assert(messageList[0].isBlank())
        assert(messageList[1] == "${AvReceiver.MODULE_NOT_SUPPORTED_ERROR} NONSENSE")
        assert(messageList[2] == "${AvReceiver.MODULE_NOT_SUPPORTED_ERROR} NONSENSE")
*/
        verifySequence {
            mainJobLogic.getMainJobEntry(JobDataProvider.DUMMY_JOB_ID)
            mapper.renderingJobToCacheObject(job)
            subJobRepository.save(any())
            audioModule.module()
            videoModule.module()
            subJobRepository.findByIdOrNull(ObjectId(JobDataProvider.SUB_ID_1))
            subJobRepository.save(any())
            mainJobLogic.processMainJob(JobDataProvider.DUMMY_JOB_ID)
        }

    }
}
*/
