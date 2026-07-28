package org.edu_sharing.rendering.renderingJob.queue

import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.bson.types.ObjectId
import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.edusharingRepo.services.ContentTransferService
import org.edu_sharing.rendering.modules.ModuleRegistry
import org.edu_sharing.rendering.modules.RenderModule
import org.edu_sharing.rendering.modules.image.ImageRenderModule
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.RenderingJobStatus
import org.edu_sharing.rendering.renderingJob.entity.SubJobStatus
import org.edu_sharing.rendering.renderingJob.repository.RenderingJobRepository
import org.edu_sharing.rendering.storage.StorageService
import org.edu_sharing.rendering.testUtils.JobDataProvider
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.util.Optional

class JobReceiverTest {
    private val jobRepository: RenderingJobRepository = mockk()
    private val storageService: StorageService = mockk()
    private val moduleRegistry: ModuleRegistry = mockk()
    private val mapper: Mapper = mockk()
    private val contentTransferService: ContentTransferService = mockk()
    private val jobDataProvider = JobDataProvider()

    private val underTest = JobReceiver(
        jobRepository = jobRepository,
        storageImplementation = storageService,
        mapper = mapper,
        contentTransferService = contentTransferService,
        moduleRegistry = moduleRegistry
    )

    private val jobId = JobDataProvider.DUMMY_JOB_ID

    @Test
    fun `drops redelivered message for an already terminal job`() {
        val job = jobDataProvider.getJobWithoutSubJobs(module = "IMAGE").apply {
            status = RenderingJobStatus.FINISHED
        }
        every { jobRepository.findById(ObjectId(jobId)) } returns Optional.of(job)

        underTest.receiveMessage(RenderingJobMessage(jobId))

        // No re-processing: neither a status write nor sub-job creation happens.
        verify(exactly = 0) { jobRepository.save(any()) }
        verify(exactly = 0) { moduleRegistry.getRenderModule<RenderModule>(any<String>()) }
    }

    @Test
    fun `skips duplicate sub-job creation when the job already has sub-jobs (redelivery)`() {
        val job = jobDataProvider.getJobWithoutSubJobs(module = "IMAGE").apply {
            status = RenderingJobStatus.PROCESSING
            subJobs = mutableListOf(
                jobDataProvider.getDummySubJob(JobDataProvider.SUB_ID_1, SubJobStatus.FINISHED, "IMAGE", "image/jpeg")
            )
        }
        every { jobRepository.findById(ObjectId(jobId)) } returns Optional.of(job)

        underTest.receiveMessage(RenderingJobMessage(id = jobId, missingQualities = listOf(1, 2)))

        verify(exactly = 0) { jobRepository.save(any()) }
        verify(exactly = 0) { moduleRegistry.getRenderModule<RenderModule>(any<String>()) }
    }

    @Test
    fun `creates sub-jobs on first delivery of a fresh conversion job`() {
        val job = RenderingJob(
            id = ObjectId(jobId),
            esHash = "hash",
            esObjectId = "esobjectid",
            esObjectType = "esobjecttype",
            mimeType = "image/jpeg",
            module = "IMAGE",
            nodeVersion = "1.2",
            repoId = "repoid",
            status = RenderingJobStatus.QUEUED,
            conversionType = true
        )
        val cacheObject = mockk<CacheObject>()
        val contentStream = ByteArrayInputStream("content".toByteArray())
        val conversionModule = mockk<ImageRenderModule>()
        val message = RenderingJobMessage(id = jobId, missingQualities = listOf(1, 2))

        every { jobRepository.findById(ObjectId(jobId)) } returns Optional.of(job)
        every { jobRepository.save(any()) } returns job
        every { mapper.renderingJobToCacheObject(job) } returns cacheObject
        every { contentTransferService.getAsInputStream(cacheObject) } returns contentStream
        justRun { storageService.putTempFile(cacheObject, contentStream) }
        every { moduleRegistry.getRenderModule<RenderModule>("IMAGE") } returns conversionModule
        justRun { conversionModule.createConversionSubJobs(job, message) }

        underTest.receiveMessage(message)

        verify(exactly = 1) { conversionModule.createConversionSubJobs(job, message) }
    }
}
