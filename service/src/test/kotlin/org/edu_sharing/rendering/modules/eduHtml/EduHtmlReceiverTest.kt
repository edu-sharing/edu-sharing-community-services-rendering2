package org.edu_sharing.rendering.modules.eduHtml

import io.mockk.mockk
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.modules.eduhtml.EduHtmlConversionService
import org.edu_sharing.rendering.modules.eduhtml.EduHtmlReceiver
import org.edu_sharing.rendering.modules.eduhtml.EduHtmlService
import org.edu_sharing.rendering.renderingJob.MainJobLogic
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository

class EduHtmlReceiverTest {
    private val eduHtmlService: EduHtmlService = mockk()
    private val eduHtmlConversionService: EduHtmlConversionService = mockk()
    private val subJobRepository: SubJobRepository = mockk()
    private val mainJobLogic: MainJobLogic = mockk()
    private val mapper = Mapper()
    private val underTest = EduHtmlReceiver(
        eduHtmlService,
        eduHtmlConversionService,
        subJobRepository,mainJobLogic,
        mapper
    )

/*
    @Test
    fun testReceiveMessageReturnsEarlyIfNoMainJobFound() {
        // Arrange
        val id = "507f191e810c19729de860eb"
        val message = RenderingJobMessage(id = id)
        every { mainJobLogic.getMainJobEntry(id) } returns null
        // Act
        underTest.receiveMessage(message)
        // Assert
        verify(exactly = 1) { mainJobLogic.getMainJobEntry(id) }
        confirmVerified(mainJobLogic)
    }

    @Test
    fun testReceiveMessageReturnsEarlyIfMainJobHasNoSubJobs() {
        // Arrange
        val id = "507f191e810c19729de860ea"
        val message = RenderingJobMessage(id = id)
        val job = prepareJobForTesting(id)
        every { mainJobLogic.getMainJobEntry(id) } returns job
        // Act
        underTest.receiveMessage(message)
        // Assert
        verify(exactly = 1) { mainJobLogic.getMainJobEntry(id) }
        confirmVerified(mainJobLogic)
    }


    @Test
    fun testReceiveMessageSetsSubJobToFailedIfCachingFails() {
        // Arrange
        val id = "507f191e810c19729de860ea"
        val subId = "507f191e810c19729de860eb"
        val message = RenderingJobMessage(id = id)
        val job = prepareJobForTesting(id = id, subId = subId)
        val cacheObject = mapper.renderingJobToCacheObject(job)
        val failedSubJob = getDummySubJob(subId, JobStatus.FAILED)
        every { mainJobLogic.getMainJobEntry(id) } returns job
        every { eduHtmlConversionService.cacheData(cacheObject) } throws Exception()
        every { mainJobLogic.processMainJob(id) } returns true
        every { subJobRepository.save(failedSubJob) } returns failedSubJob
        // Act
        underTest.receiveMessage(message)
        // Assert
        verify(exactly = 1) { mainJobLogic.getMainJobEntry(id) }
        verify(exactly = 1) { eduHtmlConversionService.cacheData(cacheObject) }
        verify(exactly = 1) { mainJobLogic.processMainJob(id) }
        verify(exactly = 1) { subJobRepository.save(failedSubJob) }
        confirmVerified(mainJobLogic,eduHtmlConversionService, mainJobLogic)
    }

    @Test
    fun testReceiveMessageSetsSubJobToFailedIfRetrievingSubJobFails() {
        // Arrange
        val id = "507f191e810c19729de860ea"
        val subId = "507f191e810c19729de860eb"
        val message = RenderingJobMessage(id = id)
        val job = prepareJobForTesting(id = id, subId = subId)
        val cacheObject = mapper.renderingJobToCacheObject(job)
        val failedSubJob = getDummySubJob(subId, JobStatus.FAILED)
        every { mainJobLogic.getMainJobEntry(id) } returns job
        justRun { eduHtmlConversionService.cacheData(cacheObject) }
        every { mainJobLogic.processMainJob(id) } returns true
        every { subJobRepository.save(failedSubJob) } returns failedSubJob
        every { eduHtmlService.getObjectLink(cacheObject = cacheObject)} throws Exception()
        // Act
        underTest.receiveMessage(message)
        // Assert
        verify(exactly = 1) { mainJobLogic.getMainJobEntry(id) }
        verify(exactly = 1) { eduHtmlConversionService.cacheData(cacheObject) }
        verify(exactly = 1) { mainJobLogic.processMainJob(id) }
        verify(exactly = 1) { subJobRepository.save(failedSubJob) }
        verify(exactly = 1) { eduHtmlService.getObjectLink(cacheObject = cacheObject) }
        confirmVerified(mainJobLogic,eduHtmlConversionService, mainJobLogic)
    }

    @Test
    fun testReceiveMessageSetsSubJobToSuccessIfCachingSucceeds() {
        // Arrange
        val id = "507f191e810c19729de860ea"
        val subId = "507f191e810c19729de860eb"
        val message = RenderingJobMessage(id = id)
        val job = prepareJobForTesting(id = id, subId = subId)
        val cacheObject = mapper.renderingJobToCacheObject(job)
        val link = "success"
        val successfulSubJob = getDummySubJob(subId, JobStatus.FINISHED)
        successfulSubJob.message = link
        every { mainJobLogic.getMainJobEntry(id) } returns job
        justRun { eduHtmlConversionService.cacheData(cacheObject) }
        every { mainJobLogic.processMainJob(id) } returns true
        every { subJobRepository.save(successfulSubJob) } returns successfulSubJob
        every { eduHtmlService.getObjectLink(cacheObject = cacheObject)} returns ObjectLink(link = link)
        // Act
        underTest.receiveMessage(message)
        // Assert
        verify(exactly = 1) { mainJobLogic.getMainJobEntry(id) }
        verify(exactly = 1) { eduHtmlConversionService.cacheData(cacheObject) }
        verify(exactly = 1) { mainJobLogic.processMainJob(id) }
        verify(exactly = 1) { subJobRepository.save(successfulSubJob) }
        verify(exactly = 1) { eduHtmlService.getObjectLink(cacheObject = cacheObject) }
        confirmVerified(mainJobLogic,eduHtmlConversionService, mainJobLogic)
    }

    private fun prepareJobForTesting(id: String, subId: String? = null): RenderingJob {
        val job = RenderingJob(
            id = ObjectId(id),
            esHash = "hash",
            esObjectId = JobDataProvider.ES_OBJECT_ID,
            esObjectType = "esobjecttype",
            mimeType = "image/jpeg",
            module = "EDUHTML",
            nodeVersion = "1.2",
            repoId = "repoid",
            status = JobStatus.PROCESSING
        )
        if (subId != null) {
            job.subJobs = mutableListOf(getDummySubJob(subId, JobStatus.QUEUED))
        }
        return job
    }

    private fun getDummySubJob(subId: String, status: JobStatus): SubJob {
        // Sub jobs need a fake parent
        val dummy = RenderingJob(
            id = ObjectId(JobDataProvider.DUMMY_JOB_ID),
            esHash = "hash",
            esObjectId = JobDataProvider.ES_OBJECT_ID,
            esObjectType = "esobjecttype",
            mimeType = "image/jpeg",
            module = "IMAGE",
            nodeVersion = "1.2",
            repoId = "repoid",
            status = JobStatus.PROCESSING,
            creationTimestamp = JobDataProvider.DUMMY_CREATION_TS
        )
        val subJob = SubJob(
            id = ObjectId(subId),
            parent = dummy,
            routingKey = "whatever",
            status = status
        )
        return subJob
    }
*/

}
