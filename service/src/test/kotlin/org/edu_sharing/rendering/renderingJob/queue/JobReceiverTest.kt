package org.edu_sharing.rendering.renderingJob.queue

import io.mockk.*
import org.bson.types.ObjectId
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.edusharingRepo.services.ContentTransferService
import org.edu_sharing.rendering.modules.ModuleRegistry
import org.edu_sharing.rendering.modules.RenderModule
import org.edu_sharing.rendering.modules.image.ImageRenderModule
import org.edu_sharing.rendering.renderingJob.entity.JobStatus
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.repository.RenderingJobRepository
import org.edu_sharing.rendering.storage.StorageService
import org.junit.jupiter.api.Test
import org.springframework.data.repository.findByIdOrNull
import java.io.ByteArrayInputStream

class JobReceiverTest {
    private val jobRepository: RenderingJobRepository = mockk()
    private val storageService: StorageService = mockk()
    private val moduleRegistry: ModuleRegistry = mockk()
    private val mapper = Mapper()
    private val contentTransferService: ContentTransferService = mockk()
    private val underTest = JobReceiver(
        jobRepository = jobRepository,
        storageImplementation = storageService,
        mapper = mapper,
        contentTransferService = contentTransferService,
        moduleRegistry = moduleRegistry
    )

    @Test
    fun testReceiveMessageReturnsIfNoJobFound() {
        val jobId = "507f191e810c19729de860ea"
        every { jobRepository.findByIdOrNull(ObjectId(jobId)) } returns null
        val message = RenderingJobMessage(jobId)
        underTest.receiveMessage(message)
        verify(exactly = 1) { jobRepository.findByIdOrNull(ObjectId(jobId)) }
        confirmVerified(jobRepository)
    }

    @Test
    fun testReceiveMessageSetsJobToFailedAndReturnsIfPuttingTempFileFails() {
        val jobId = "507f191e810c19729de860ea"
        val job = prepareJobForTesting(jobId)
        val cacheObject = mapper.renderingJobToCacheObject(job)
        val contentInputStream = ByteArrayInputStream("coolcontent".toByteArray())
        val message = RenderingJobMessage(jobId)
        every { jobRepository.findByIdOrNull(ObjectId(jobId)) } returns job
        every { contentTransferService.getAsInputStream(cacheObject) } returns contentInputStream
        every { storageService.putTempFile(cacheObject, contentInputStream) } throws Exception()
        every { jobRepository.save(any()) } returns job
        underTest.receiveMessage(message)
        verify(exactly = 1) { jobRepository.findByIdOrNull(ObjectId(jobId)) }
        verify(exactly = 1) { contentTransferService.getAsInputStream(cacheObject) }
        verify(exactly = 1) { storageService.putTempFile(cacheObject, contentInputStream) }
        verify(exactly = 2) { jobRepository.save(any()) }
        assert(job.status == JobStatus.FAILED)
        confirmVerified(jobRepository, contentTransferService, storageService, jobRepository)
    }

    @Test
    fun testReceiveMessageFetchesConversionModuleAndCallsJobCreation() {

        // Arrange
        val jobId = "507f191e810c19729de860ea"
        val job = prepareJobForTesting(jobId, "IMAGE")
        val cacheObject = mapper.renderingJobToCacheObject(job)
        val contentInputStream = ByteArrayInputStream("coolcontent".toByteArray())
        val message = RenderingJobMessage(id = jobId, missingQualities = listOf(1, 2))
        val conversionModule = mockk<ImageRenderModule>()
        val submittedJobSlot = slot<RenderingJob>()
        val savedJobSlot = slot<RenderingJob>()

        every { jobRepository.findByIdOrNull(ObjectId(jobId)) } returns job
        every { contentTransferService.getAsInputStream(cacheObject) } returns contentInputStream
        justRun { storageService.putTempFile(cacheObject, contentInputStream) }
        every { jobRepository.save(capture(savedJobSlot)) } returns job
        every { moduleRegistry.getRenderModule<RenderModule>("IMAGE")} returns conversionModule
        justRun { conversionModule.createJob(capture(submittedJobSlot), message) }

        // Act
        underTest.receiveMessage(message)

        // Assert
        assert(savedJobSlot.isCaptured)
        assert(savedJobSlot.captured.id == job.id)
        assert(savedJobSlot.captured.status == JobStatus.PROCESSING)

        assert(submittedJobSlot.isCaptured)
        assert(submittedJobSlot.captured.id == job.id)
        assert(submittedJobSlot.captured.status == JobStatus.PROCESSING)

        verifySequence {
            jobRepository.findByIdOrNull(ObjectId(jobId))
            jobRepository.save(any())
            contentTransferService.getAsInputStream(cacheObject)
            storageService.putTempFile(cacheObject, contentInputStream)
            moduleRegistry.getRenderModule<RenderModule>("IMAGE")
            conversionModule.createJob(capture(submittedJobSlot), message)
        }

        confirmVerified(
            jobRepository,
            contentTransferService,
            storageService,
            moduleRegistry,
        )
    }

    private fun prepareJobForTesting(
        id: String,
        module: String = "VIDEO",
        conversionType: Boolean = true
    ): RenderingJob {
        val job = RenderingJob(
            id = ObjectId(id),
            esHash = "hash",
            esObjectId = "esobjectid",
            esObjectType = "esobjecttype",
            mimeType = "multipart/form-data",
            module = module,
            nodeVersion = "1.2",
            repoId = "repoid",
            status = JobStatus.QUEUED,
            conversionType = conversionType
        )
        return job
    }

}
