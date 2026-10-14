package org.edu_sharing.rendering.modules.av

import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.modules.av.audio.AudioConversionService
import org.edu_sharing.rendering.modules.av.audio.AudioRenderModule
import org.edu_sharing.rendering.modules.av.video.VideoConversionService
import org.edu_sharing.rendering.modules.av.video.VideoRenderModule
import org.edu_sharing.rendering.renderingJob.MainJobLogic
import org.edu_sharing.rendering.renderingJob.entity.SubJobStatus
import org.edu_sharing.rendering.renderingJob.queue.SubJobMessage
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.edu_sharing.rendering.storage.StorageService
import org.edu_sharing.rendering.testUtils.JobDataProvider
import org.junit.jupiter.api.Test

/** At-least-once redelivery guards of [AvReceiver] (the older AvReceiverTest is disabled). */
class AvReceiverRedeliveryTest {
    private val mainJobLogic: MainJobLogic = mockk()
    private val subJobRepository: SubJobRepository = mockk()
    private val audioConversionService: AudioConversionService = mockk()
    private val videoConversionService: VideoConversionService = mockk()
    private val mapper: Mapper = mockk()
    private val storageService: StorageService = mockk()
    private val audioModule: AudioRenderModule = mockk()
    private val videoModule: VideoRenderModule = mockk()
    private val underTest = AvReceiver(
        mainJobLogic, subJobRepository, audioConversionService, videoConversionService,
        mapper, storageService, audioModule, videoModule
    )
    private val jobDataProvider = JobDataProvider()
    private val jobId = JobDataProvider.DUMMY_JOB_ID

    private fun jobWith(status: SubJobStatus) = jobDataProvider.getJobWithoutSubJobs(module = "VIDEO").apply {
        subJobs = mutableListOf(
            jobDataProvider.getDummySubJob(JobDataProvider.SUB_ID_1, status, "VIDEO", "video/mp4", quality = 720)
        )
    }

    @Test
    fun `redelivery for a terminal sub-job does not convert again but resolves the main job`() {
        val job = jobWith(SubJobStatus.FINISHED)
        val cacheObject = mockk<CacheObject>()
        every { mainJobLogic.getMainJobEntry(jobId) } returns job
        every { mapper.renderingJobToCacheObject(job) } returns cacheObject
        every { mainJobLogic.processMainJob(jobId) } returns true
        justRun { storageService.removeTempObject(cacheObject) }

        underTest.receiveMessage(SubJobMessage(id = jobId, quality = 720))

        verify(exactly = 0) { subJobRepository.save(any()) }
        verify(exactly = 0) { videoConversionService.convert(any(), any()) }
        verify(exactly = 1) { mainJobLogic.processMainJob(jobId) }
    }

    @Test
    fun `redelivery for a PROCESSING sub-job restarts the conversion`() {
        val job = jobWith(SubJobStatus.PROCESSING)
        val subJob = job.subJobs[0]
        val cacheObject = mockk<CacheObject>(relaxed = true)
        every { mainJobLogic.getMainJobEntry(jobId) } returns job
        every { mapper.renderingJobToCacheObject(job) } returns cacheObject
        every { subJobRepository.save(any()) } answers { firstArg() }
        every { subJobRepository.findById(subJob.id) } returns java.util.Optional.of(subJob)
        every { videoModule.module() } returns "VIDEO"
        every { audioModule.module() } returns "AUDIO"
        every { cacheObject.deepCopy() } returns cacheObject
        every { cacheObject.nodeId } returns "node"
        justRun { videoConversionService.convert(cacheObject, any()) }
        every { mainJobLogic.processMainJob(jobId) } returns false

        underTest.receiveMessage(SubJobMessage(id = jobId, quality = 720))

        verify(exactly = 1) { videoConversionService.convert(cacheObject, any()) }
        assert(subJob.status == SubJobStatus.FINISHED)
    }
}
