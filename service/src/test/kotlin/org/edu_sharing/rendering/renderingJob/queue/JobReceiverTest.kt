package org.edu_sharing.rendering.renderingJob.queue

import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
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
        justRun { storageService.putTempFile(cacheObject, any()) }
        every { moduleRegistry.getRenderModule<RenderModule>("IMAGE") } returns conversionModule
        justRun { conversionModule.createConversionSubJobs(job, message) }

        underTest.receiveMessage(message)

        verify(exactly = 1) { conversionModule.createConversionSubJobs(job, message) }
    }

    /**
     * `JobReceiver` hands the storage layer a *supplier* of the content stream (see
     * `S3StorageService.putObjectStreaming`): the supplier is invoked again with a fresh, unread
     * stream whenever a retry needs to re-read the payload from the start, so `JobReceiver` itself
     * no longer opens or closes a stream directly. These three cases pin that contract down: the
     * supplier passed to the storage service, when invoked, returns exactly what
     * `ContentTransferService.getAsInputStream` produces.
     */
    @Test
    fun `passes a supplier that fetches the content stream for the conversion input`() {
        val job = freshJob(conversionType = true)
        val cacheObject = mockk<CacheObject>()
        val contentStream = TrackingInputStream("content")
        val conversionModule = mockk<ImageRenderModule>()
        val message = RenderingJobMessage(id = jobId, missingQualities = listOf(1, 2))
        val streamProviderSlot = slot<() -> java.io.InputStream>()

        every { jobRepository.findById(ObjectId(jobId)) } returns Optional.of(job)
        every { jobRepository.save(any()) } returns job
        every { mapper.renderingJobToCacheObject(job) } returns cacheObject
        every { contentTransferService.getAsInputStream(cacheObject) } returns contentStream
        justRun { storageService.putTempFile(cacheObject, capture(streamProviderSlot)) }
        every { moduleRegistry.getRenderModule<RenderModule>("IMAGE") } returns conversionModule
        justRun { conversionModule.createConversionSubJobs(job, message) }

        underTest.receiveMessage(message)

        assert(streamProviderSlot.captured.invoke() === contentStream) {
            "supplier passed to putTempFile must (re-)fetch the content stream"
        }
    }

    @Test
    fun `passes a supplier that fetches the content stream for a non-conversion object`() {
        val job = freshJob(conversionType = false)
        val cacheObject = mockk<CacheObject>()
        val contentStream = TrackingInputStream("content")
        val streamProviderSlot = slot<() -> java.io.InputStream>()

        every { jobRepository.findById(ObjectId(jobId)) } returns Optional.of(job)
        every { jobRepository.save(any()) } returns job
        every { mapper.renderingJobToCacheObject(job) } returns cacheObject
        every { contentTransferService.getAsInputStream(cacheObject) } returns contentStream
        justRun { storageService.putObject(cacheObject, capture(streamProviderSlot), any()) }

        underTest.receiveMessage(RenderingJobMessage(jobId))

        assert(streamProviderSlot.captured.invoke() === contentStream) {
            "supplier passed to putObject must (re-)fetch the content stream"
        }
        assert(job.status == RenderingJobStatus.FINISHED)
    }

    @Test
    fun `marks the job failed when the upload throws`() {
        val job = freshJob(conversionType = true)
        val cacheObject = mockk<CacheObject>()
        val contentStream = TrackingInputStream("content")

        every { jobRepository.findById(ObjectId(jobId)) } returns Optional.of(job)
        every { jobRepository.save(any()) } returns job
        every { mapper.renderingJobToCacheObject(job) } returns cacheObject
        every { contentTransferService.getAsInputStream(cacheObject) } returns contentStream
        every { storageService.putTempFile(cacheObject, any()) } throws RuntimeException("S3 down")

        underTest.receiveMessage(RenderingJobMessage(jobId))

        assert(job.status == RenderingJobStatus.FAILED)
    }

    @Test
    fun `falls back to targeted updates when saving FAILED fails after a Mongo blip`() {
        underTest.failureRetryDelayMillis = 0
        val job = freshJob(conversionType = true)
        val cacheObject = mockk<CacheObject>()
        every { jobRepository.findById(ObjectId(jobId)) } returns Optional.of(job)
        // First save (PROCESSING) works, the second one (FAILED) hits the blip.
        every { jobRepository.save(any()) } returns job andThenThrows RuntimeException("mongo blip")
        every { mapper.renderingJobToCacheObject(job) } returns cacheObject
        every { storageService.putTempFile(cacheObject, any()) } throws RuntimeException("S3 down")
        justRun { jobRepository.updateErrorMessageWithoutVersion(job.id, any()) }
        justRun { jobRepository.updateStatusWithoutVersion(job.id, RenderingJobStatus.FAILED) }

        underTest.receiveMessage(RenderingJobMessage(jobId))

        verify(exactly = 1) { jobRepository.updateStatusWithoutVersion(job.id, RenderingJobStatus.FAILED) }
        verify(exactly = 1) { jobRepository.updateErrorMessageWithoutVersion(job.id, any()) }
    }

    @Test
    fun `never lets an exception escape the listener when persisting FAILED fails twice`() {
        underTest.failureRetryDelayMillis = 0
        val job = freshJob(conversionType = true)
        val cacheObject = mockk<CacheObject>()
        every { jobRepository.findById(ObjectId(jobId)) } returns Optional.of(job)
        every { jobRepository.save(any()) } returns job andThenThrows RuntimeException("mongo down")
        every { mapper.renderingJobToCacheObject(job) } returns cacheObject
        every { storageService.putTempFile(cacheObject, any()) } throws RuntimeException("S3 down")
        every { jobRepository.updateErrorMessageWithoutVersion(any(), any()) } throws RuntimeException("mongo down")

        // Must not throw: the stale-job reaper resolves the job (PROCESSING without sub-jobs) later.
        underTest.receiveMessage(RenderingJobMessage(jobId))

        verify(exactly = 0) { jobRepository.updateStatusWithoutVersion(any(), any()) }
    }

    private fun freshJob(conversionType: Boolean) = RenderingJob(
        id = ObjectId(jobId),
        esHash = "hash",
        esObjectId = "esobjectid",
        esObjectType = "esobjecttype",
        mimeType = "image/jpeg",
        module = "IMAGE",
        nodeVersion = "1.2",
        repoId = "repoid",
        status = RenderingJobStatus.QUEUED,
        conversionType = conversionType
    )

    private class TrackingInputStream(content: String) : ByteArrayInputStream(content.toByteArray()) {
        var closed = false
            private set

        override fun close() {
            closed = true
            super.close()
        }
    }
}
