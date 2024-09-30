package org.edu_sharing.rendering.modules.document

import io.mockk.*
import org.bson.types.ObjectId
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.modules.ModuleRegistry
import org.edu_sharing.rendering.modules.RenderModule
import org.edu_sharing.rendering.renderingJob.MainJobLogic
import org.edu_sharing.rendering.renderingJob.entity.JobStatus
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.edu_sharing.rendering.renderingJob.queue.RenderingJobMessage
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.edu_sharing.rendering.testUtils.JobDataProvider
import org.junit.jupiter.api.Test

class DocumentReceiverTest {
    private val mainJobLogic: MainJobLogic = mockk()
    private val mapper = Mapper()
    private val documentConversionService: DocumentConversionService = mockk()
    private val subJobRepository: SubJobRepository = mockk()
    private val moduleRegistry: ModuleRegistry = mockk()
    private val underTest = DocumentReceiver(
        mainJobLogic = mainJobLogic,
        mapper = mapper,
        documentConversionService = documentConversionService,
        subJobRepository = subJobRepository,
        moduleRegistry = moduleRegistry
    )
    private val module: DocumentRenderModule = mockk()

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
    fun testReceiveMessageSetsSubJobToFailedIfConversionFails() {
        // Arrange
        val id = "507f191e810c19729de860ea"
        val subId = "507f191e810c19729de860eb"
        val message = RenderingJobMessage(id = id)
        val job = prepareJobForTesting(id = id, subId = subId)
        val cacheObject = mapper.renderingJobToCacheObject(job)
        val failedSubJob = getDummySubJob(subId, JobStatus.FAILED)
        failedSubJob.message = DocumentReceiver.PUBLIC_FAILURE_MESSAGE
        every { mainJobLogic.getMainJobEntry(id) } returns job
        every { subJobRepository.save(any()) } returns failedSubJob
        every { moduleRegistry.getRenderModule<RenderModule>(moduleName = "DOCUMENT") } returns module
        every { documentConversionService.convertAndMoveToCache(cacheObject, module) } throws Exception("")
        every { mainJobLogic.processMainJob(id) } returns true

        // Act
        underTest.receiveMessage(message)
        // Assert
        verify(exactly = 1) { mainJobLogic.getMainJobEntry(id) }
        verify(exactly = 1) { documentConversionService.convertAndMoveToCache(cacheObject, module) }
        verify(exactly = 1) { mainJobLogic.processMainJob(id) }
        verifyOrder {
            subJobRepository.save(any())
            subJobRepository.save(failedSubJob)
        }
        confirmVerified(mainJobLogic, documentConversionService, mainJobLogic)
    }

    @Test
    fun testReceiveMessageSetsSubJobToSuccessIfConversionSucceeds() {
        // Arrange
        val id = "507f191e810c19729de860ea"
        val subId = "507f191e810c19729de860eb"
        val message = RenderingJobMessage(id = id)
        val job = prepareJobForTesting(id = id, subId = subId)
        val cacheObject = mapper.renderingJobToCacheObject(job)
        val successfulSubJob = getDummySubJob(subId, JobStatus.FINISHED)
        every { mainJobLogic.getMainJobEntry(id) } returns job
        every { subJobRepository.save(any()) } returns successfulSubJob
        every { moduleRegistry.getRenderModule<RenderModule>(moduleName = "DOCUMENT") } returns module
        justRun { documentConversionService.convertAndMoveToCache(cacheObject, module) }
        every { mainJobLogic.processMainJob(id) } returns true

        // Act
        underTest.receiveMessage(message)
        // Assert
        verify(exactly = 1) { mainJobLogic.getMainJobEntry(id) }
        verify(exactly = 1) { documentConversionService.convertAndMoveToCache(cacheObject, module) }
        verify(exactly = 1) { mainJobLogic.processMainJob(id) }
        verifyOrder {
            subJobRepository.save(any())
            subJobRepository.save(successfulSubJob)
        }
        confirmVerified(mainJobLogic, documentConversionService, mainJobLogic)
    }

    private fun prepareJobForTesting(id: String, subId: String? = null): RenderingJob {
        val job = RenderingJob(
            id = ObjectId(id),
            esHash = "hash",
            esObjectId = JobDataProvider.ES_OBJECT_ID,
            esObjectType = "esobjecttype",
            mimeType = "image/jpeg",
            module = "DOCUMENT",
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
}