package org.edu_sharing.rendering.modules.eduHtml

import io.mockk.*
import org.bson.types.ObjectId
import org.edu_sharing.rendering.core.ErrorStrings.GENERIC_CONVERSION_ERROR
import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.modules.eduhtml.EduHtmlConversionService
import org.edu_sharing.rendering.modules.eduhtml.EduHtmlReceiver
import org.edu_sharing.rendering.modules.eduhtml.EduHtmlService
import org.edu_sharing.rendering.renderingJob.MainJobLogic
import org.edu_sharing.rendering.renderingJob.SubJobHeartbeat
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.RenderingJobStatus
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.edu_sharing.rendering.renderingJob.entity.SubJobStatus
import org.edu_sharing.rendering.renderingJob.queue.RenderingJobMessage
import org.edu_sharing.rendering.renderingJob.repository.RenderingJobRepository
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.junit.jupiter.api.Test

class EduHtmlReceiverTest {
    private val eduHtmlService: EduHtmlService = mockk()
    private val eduHtmlConversionService: EduHtmlConversionService = mockk()
    private val subJobRepository: SubJobRepository = mockk()
    private val mainJobLogic: MainJobLogic = mockk()
    private val renderingJobRepository: RenderingJobRepository = mockk()
    private val mapper = mockk<Mapper>()
    // Real instance (not a mock): run() just executes the block synchronously, and the periodic
    // touch (every 5 min) never fires within a unit test's lifetime, so no stubbing needed.
    private val subJobHeartbeat = SubJobHeartbeat(mockk(relaxed = true))
    private val underTest = EduHtmlReceiver(
        eduHtmlService,
        eduHtmlConversionService,
        subJobRepository,mainJobLogic,
        mapper,
        renderingJobRepository,
        subJobHeartbeat
    )

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
        val job = mockk<RenderingJob>()
        every { mainJobLogic.getMainJobEntry(id) } returns job
        every { job.subJobs } returns mutableListOf()
        // Act
        underTest.receiveMessage(message)
        // Assert
        verify(exactly = 1) { mainJobLogic.getMainJobEntry(id) }
        confirmVerified(mainJobLogic)
    }

    @Test
    fun testReceiveMessageSetsSubJobToFailedIfCachingFails() {
        // Arrange
        val message = mockk<RenderingJobMessage>()
        val job = mockk<RenderingJob>()
        val subJob = mockk<SubJob>()
        val cacheObject = mockk<CacheObject>()
        val jobId = ObjectId()
        val subJobId = ObjectId()

        val candidates = listOf("index.html", "index.htm", "story.html")

        every { message.id } returns "id"
        every { job.subJobs } returns mutableListOf(subJob)
        every { mainJobLogic.getMainJobEntry("id") } returns job
        every { job.id } returns jobId
        every { subJob.id } returns subJobId
        justRun { renderingJobRepository.updateStatusWithoutVersion(jobId, RenderingJobStatus.PROCESSING) }
        justRun { subJob.processingStartedDate = any() }
        every  { subJobRepository.save(subJob) } returns subJob
        every { mapper.renderingJobToCacheObject(job) } returns cacheObject
        every { subJob.additionalData } returns null
        every { eduHtmlService.entryCandidates(null) } returns candidates
        every { eduHtmlConversionService.cacheData(cacheObject, candidates) } throws Exception("testException")
        justRun { subJob.errorMessage = GENERIC_CONVERSION_ERROR }
        justRun { subJob.status = SubJobStatus.FAILED }
        justRun { subJob.finishedDate = any() }
        every { mainJobLogic.processMainJob(jobId.toString()) } returns true

        excludeRecords {
            message.id
            job.subJobs
            job.id
            subJob.id
            subJob.additionalData
        }

        // Act
        underTest.receiveMessage(message)

        // Assert
        verifySequence {
            mainJobLogic.getMainJobEntry("id")
            renderingJobRepository.updateStatusWithoutVersion(jobId, RenderingJobStatus.PROCESSING)
            subJob.processingStartedDate = any()
            subJobRepository.save(subJob)
            mapper.renderingJobToCacheObject(job)
            eduHtmlService.entryCandidates(null)
            eduHtmlConversionService.cacheData(cacheObject, candidates)
            subJob.errorMessage = GENERIC_CONVERSION_ERROR
            subJob.status = SubJobStatus.FAILED
            subJob.finishedDate = any()
            subJobRepository.save(subJob)
            mainJobLogic.processMainJob(jobId.toString())
        }
    }

    @Test
    fun testReceiveMessagePassesMainEntityCandidatesFromAdditionalData() {
        // Arrange
        val message = mockk<RenderingJobMessage>()
        val job = mockk<RenderingJob>()
        val subJob = mockk<SubJob>()
        val cacheObject = mockk<CacheObject>()
        val jobId = ObjectId()
        val subJobId = ObjectId()
        val candidates = listOf("player.html")

        every { message.id } returns "id"
        every { job.subJobs } returns mutableListOf(subJob)
        every { mainJobLogic.getMainJobEntry("id") } returns job
        every { job.id } returns jobId
        every { subJob.id } returns subJobId
        justRun { renderingJobRepository.updateStatusWithoutVersion(jobId, RenderingJobStatus.PROCESSING) }
        justRun { subJob.processingStartedDate = any() }
        every { subJobRepository.save(subJob) } returns subJob
        every { mapper.renderingJobToCacheObject(job) } returns cacheObject
        every { subJob.additionalData } returns mapOf("mainEntity" to "player.html")
        every { eduHtmlService.entryCandidates("player.html") } returns candidates
        justRun { eduHtmlConversionService.cacheData(cacheObject, candidates) }
        every { eduHtmlService.getObjectLink(cacheObject, candidates) } returns
            org.edu_sharing.rendering.core.dto.ObjectLink(link = "ok")
        justRun { subJob.message = "ok" }
        justRun { subJob.status = SubJobStatus.FINISHED }
        justRun { subJob.finishedDate = any() }
        every { mainJobLogic.processMainJob(jobId.toString()) } returns true

        excludeRecords {
            message.id
            job.subJobs
            job.id
            subJob.id
            subJob.additionalData
        }

        // Act
        underTest.receiveMessage(message)

        // Assert
        verify(exactly = 1) { eduHtmlService.entryCandidates("player.html") }
        verify(exactly = 1) { eduHtmlConversionService.cacheData(cacheObject, candidates) }
        verify(exactly = 1) { eduHtmlService.getObjectLink(cacheObject, candidates) }
        verify(exactly = 1) { subJob.message = "ok" }
    }

    /*
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

     */
}
