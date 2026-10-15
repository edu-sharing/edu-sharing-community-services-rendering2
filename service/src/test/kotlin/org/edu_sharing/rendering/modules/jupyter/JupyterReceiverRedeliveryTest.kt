package org.edu_sharing.rendering.modules.jupyter

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.renderingJob.MainJobLogic
import org.edu_sharing.rendering.renderingJob.entity.SubJobStatus
import org.edu_sharing.rendering.renderingJob.queue.RenderingJobMessage
import org.edu_sharing.rendering.renderingJob.repository.RenderingJobRepository
import org.edu_sharing.rendering.testUtils.JobDataProvider
import org.junit.jupiter.api.Test

/** Redelivery guard (inherited from AbstractReceiver) as seen through [JupyterReceiver]. */
class JupyterReceiverRedeliveryTest {
    private val mainJobLogic: MainJobLogic = mockk()
    private val mapper: Mapper = mockk()
    private val conversionService: JupyterConversionService = mockk()
    private val renderingJobRepository: RenderingJobRepository = mockk()
    private val underTest = JupyterReceiver(mainJobLogic, mapper, conversionService, renderingJobRepository)
    private val jobDataProvider = JobDataProvider()
    private val jobId = JobDataProvider.DUMMY_JOB_ID

    @Test
    fun `redelivery for a terminal sub-job does not convert again but resolves the main job`() {
        val job = jobDataProvider.getJobWithoutSubJobs(module = "JUPYTER").apply {
            subJobs = mutableListOf(
                jobDataProvider.getDummySubJob(JobDataProvider.SUB_ID_1, SubJobStatus.FAILED, "JUPYTER", "application/x-ipynb+json")
            )
        }
        every { mainJobLogic.getMainJobEntry(jobId) } returns job
        every { mainJobLogic.processMainJob(jobId) } returns true

        underTest.receiveMessage(RenderingJobMessage(jobId))

        verify(exactly = 0) { conversionService.process(any(), any()) }
        verify(exactly = 0) { renderingJobRepository.save(any<org.edu_sharing.rendering.renderingJob.entity.RenderingJob>()) }
        verify(exactly = 1) { mainJobLogic.processMainJob(jobId) }
    }
}
