package org.edu_sharing.rendering.modules.h5p

import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.edu_sharing.rendering.config.AppInfo
import org.edu_sharing.rendering.config.H5P_BASE_PATH
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.modules.h5p.lumi.LumiContentManagementService
import org.edu_sharing.rendering.renderingJob.MainJobLogic
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.RenderingJobStatus
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.edu_sharing.rendering.renderingJob.entity.SubJobStatus
import org.edu_sharing.rendering.renderingJob.queue.RenderingJobMessage
import org.edu_sharing.rendering.renderingJob.repository.RenderingJobRepository
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.edu_sharing.rendering.testUtils.JobDataProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.amqp.core.AmqpTemplate

class H5pLookupReceiverTest {

    private val mainJobLogic = mockk<MainJobLogic>()
    private val renderingJobRepository = mockk<RenderingJobRepository>()
    private val subJobRepository = mockk<SubJobRepository>()
    private val lumiContentManagementService = mockk<LumiContentManagementService>()
    private val amqpTemplate = mockk<AmqpTemplate>()
    private val jobDataProvider = JobDataProvider()
    private val mapper = Mapper()
    private val appInfo = AppInfo()

    private val underTest = H5pLookupReceiver(
        mainJobLogic,
        renderingJobRepository,
        subJobRepository,
        lumiContentManagementService,
        amqpTemplate,
        mapper,
        appInfo
    )

    @BeforeEach
    fun setUp() {
        appInfo.public = AppInfo.ConnectionInfo("http", "localhost", 80, "", "http://localhost:80")
        underTest.topicExchangeName = EXCHANGE
        underTest.importRoutingKey = IMPORT_KEY
    }

    /** A job with its single QUEUED sub-job, as [H5pJobService] creates it. */
    private fun queuedJob(status: SubJobStatus = SubJobStatus.QUEUED): Pair<RenderingJob, SubJob> {
        val job = jobDataProvider.getJobWithoutSubJobs("H5P")
        val subJob = jobDataProvider.getDummySubJob(
            subId = JobDataProvider.SUB_ID_1,
            mimeType = "application/zip",
            module = "H5P",
            quality = 0,
            status = status
        )
        subJob.routingKey = LOOKUP_KEY
        job.subJobs.add(subJob)
        return job to subJob
    }

    @Test
    fun testReceiveMessageFinishesTheSubJobWithoutImportIfLumiAlreadyHoldsTheContent() {
        // Arrange
        val message = RenderingJobMessage("messageId")
        val (job, subJob) = queuedJob()
        val statusList = mutableListOf<SubJobStatus>()
        val subJobSlot = slot<SubJob>()
        val mainJobSlot = slot<RenderingJob>()

        every { mainJobLogic.getMainJobEntry(message.id) } returns job
        every { renderingJobRepository.save(capture(mainJobSlot)) } answers {
            assertEquals(RenderingJobStatus.PROCESSING, mainJobSlot.captured.status)
            job
        }
        every { subJobRepository.save(capture(subJobSlot)) } answers {
            statusList.add(subJobSlot.captured.status)
            subJob
        }
        every {
            lumiContentManagementService.getContentId(JobDataProvider.ES_OBJECT_ID, JobDataProvider.HASH)
        } returns "contentId"
        every { mainJobLogic.processMainJob(message.id) } returns true

        // Act
        underTest.receiveMessage(message)

        // Assert
        assertEquals(mutableListOf(SubJobStatus.PROCESSING, SubJobStatus.FINISHED), statusList)
        assertEquals("http://localhost:80$H5P_BASE_PATH/contentId", subJob.message)
        // still the lookup key: the sub-job never entered the import stage
        assertEquals(LOOKUP_KEY, subJob.routingKey)

        verify(exactly = 1) { mainJobLogic.getMainJobEntry(message.id) }
        verify(exactly = 1) { renderingJobRepository.save(any()) }
        verify(exactly = 2) { subJobRepository.save(any()) }
        verify(exactly = 1) { lumiContentManagementService.getContentId(any(), any(), any()) }
        verify(exactly = 1) { mainJobLogic.processMainJob(message.id) }
        confirmVerified(mainJobLogic, renderingJobRepository, subJobRepository, lumiContentManagementService, amqpTemplate)
    }

    @Test
    fun testReceiveMessageHandsOverToTheImportQueueOnAMiss() {
        // Arrange
        val message = RenderingJobMessage("messageId")
        val (job, subJob) = queuedJob()
        val statusList = mutableListOf<SubJobStatus>()
        val subJobSlot = slot<SubJob>()
        val publishedSlot = slot<Any>()

        every { mainJobLogic.getMainJobEntry(message.id) } returns job
        every { renderingJobRepository.save(any()) } returns job
        every { subJobRepository.save(capture(subJobSlot)) } answers {
            statusList.add(subJobSlot.captured.status)
            subJob
        }
        every {
            lumiContentManagementService.getContentId(JobDataProvider.ES_OBJECT_ID, JobDataProvider.HASH)
        } returns null
        justRun { amqpTemplate.convertAndSend(EXCHANGE, IMPORT_KEY, capture(publishedSlot)) }

        // Act
        underTest.receiveMessage(message)

        // Assert — back to QUEUED on the import key, so the reaper leaves it alone and the client's queue
        // position is computed against the import backlog
        assertEquals(mutableListOf(SubJobStatus.PROCESSING, SubJobStatus.QUEUED), statusList)
        assertEquals(IMPORT_KEY, subJob.routingKey)
        assertEquals(RenderingJobMessage(JobDataProvider.DUMMY_JOB_ID), publishedSlot.captured)

        verify(exactly = 1) { amqpTemplate.convertAndSend(EXCHANGE, IMPORT_KEY, any<Any>()) }
        // the main job must NOT be resolved yet — the import still has to run
        verify(exactly = 0) { mainJobLogic.processMainJob(any()) }
    }

    @Test
    fun testReceiveMessageHandsOverToTheImportQueueIfTheLookupFails() {
        // A lumi outage must not fail the job: the import stage repeats the lookup with its own timeout.
        // Arrange
        val message = RenderingJobMessage("messageId")
        val (job, subJob) = queuedJob()

        every { mainJobLogic.getMainJobEntry(message.id) } returns job
        every { renderingJobRepository.save(any()) } returns job
        every { subJobRepository.save(any()) } returns subJob
        every {
            lumiContentManagementService.getContentId(JobDataProvider.ES_OBJECT_ID, JobDataProvider.HASH)
        } throws IllegalStateException("lumi unreachable")
        justRun { amqpTemplate.convertAndSend(EXCHANGE, IMPORT_KEY, any<Any>()) }

        // Act
        underTest.receiveMessage(message)

        // Assert
        assertEquals(SubJobStatus.QUEUED, subJob.status)
        assertEquals(IMPORT_KEY, subJob.routingKey)
        verify(exactly = 1) { amqpTemplate.convertAndSend(EXCHANGE, IMPORT_KEY, any<Any>()) }
        verify(exactly = 0) { mainJobLogic.processMainJob(any()) }
    }

    @Test
    fun testReceiveMessageJustReturnsIfSubJobIsNotQueued() {
        // Redelivery guard: the sub-job is the stage marker for the whole job.
        // Arrange
        val message = RenderingJobMessage("messageId")
        val (job, _) = queuedJob(status = SubJobStatus.PROCESSING)
        every { mainJobLogic.getMainJobEntry(message.id) } returns job

        // Act
        underTest.receiveMessage(message)

        // Assert
        verify(exactly = 1) { mainJobLogic.getMainJobEntry(message.id) }
        confirmVerified(mainJobLogic, renderingJobRepository, subJobRepository, lumiContentManagementService, amqpTemplate)
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
        confirmVerified(mainJobLogic, renderingJobRepository, subJobRepository, lumiContentManagementService, amqpTemplate)
    }

    @Test
    fun testReceiveMessageJustReturnsIfMainJobHasNoSubJobs() {
        // Arrange
        val message = RenderingJobMessage("messageId")
        every { mainJobLogic.getMainJobEntry(message.id) } returns jobDataProvider.getJobWithoutSubJobs("H5P")

        // Act
        underTest.receiveMessage(message)

        // Assert
        verify(exactly = 1) { mainJobLogic.getMainJobEntry(message.id) }
        confirmVerified(mainJobLogic, renderingJobRepository, subJobRepository, lumiContentManagementService, amqpTemplate)
    }

    companion object {
        private const val EXCHANGE = "rendering_exchange"
        private const val LOOKUP_KEY = "h5p_lookup_job"
        private const val IMPORT_KEY = "h5p_job"
    }
}
