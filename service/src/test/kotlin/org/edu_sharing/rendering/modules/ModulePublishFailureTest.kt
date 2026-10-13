package org.edu_sharing.rendering.modules

import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.edu_sharing.generated.repository.backend.services.rest.client.model.Node
import org.edu_sharing.rendering.core.ErrorStrings
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.modules.av.audio.AudioRenderModule
import org.edu_sharing.rendering.modules.av.video.VideoConverterConfig
import org.edu_sharing.rendering.modules.av.video.VideoRenderModule
import org.edu_sharing.rendering.modules.ddb.DdbJobService
import org.edu_sharing.rendering.modules.document.DocumentRenderModule
import org.edu_sharing.rendering.modules.image.ImageRenderModule
import org.edu_sharing.rendering.modules.jupyter.JupyterRenderModule
import org.edu_sharing.rendering.renderingJob.MainJobLogic
import org.edu_sharing.rendering.renderingJob.queue.JobMessagePublisher
import org.edu_sharing.rendering.renderingJob.queue.RenderingJobMessage
import org.edu_sharing.rendering.renderingJob.repository.RenderingJobRepository
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.edu_sharing.rendering.testUtils.JobDataProvider
import org.junit.jupiter.api.Test
import org.springframework.amqp.core.MessagePostProcessor
import org.springframework.amqp.rabbit.connection.CorrelationData
import org.springframework.amqp.rabbit.core.RabbitTemplate

/**
 * Wires the modules to a real [JobMessagePublisher] on top of a broker that refuses every publish, and shows the
 * effect of RENDER-178's compensation per module: the main-job modules fail the main job, the sub-job modules
 * fail only their own queued sub-job(s) and let [MainJobLogic] resolve the main job; the exception always
 * reaches the caller.
 */
class ModulePublishFailureTest {
    private val rabbitTemplate = mockk<RabbitTemplate>()
    private val jobRepository = mockk<RenderingJobRepository>()
    private val subJobRepository = mockk<SubJobRepository>()
    private val mainJobLogic = mockk<MainJobLogic>()
    private val mapper = mockk<Mapper>()
    private val publisher = JobMessagePublisher(rabbitTemplate, jobRepository, subJobRepository)
    private val provider = JobDataProvider()

    private fun brokerDown() {
        every { rabbitTemplate.convertAndSend(any<String>(), any<String>(), any<Any>(), any<CorrelationData>()) } throws
            IllegalStateException("broker down")
        every {
            rabbitTemplate.convertAndSend(any<String>(), any<String>(), any<Any>(), any<MessagePostProcessor>(), any<CorrelationData>())
        } throws IllegalStateException("broker down")
    }

    private fun expectSubJobsFailed() {
        every { subJobRepository.save(any()) } answers { firstArg() }
        every { subJobRepository.failIfQueued(any(), ErrorStrings.GENERIC_CONVERSION_ERROR) } returns true
        justRun { mainJobLogic.processMainJob(any()) }
    }

    @Test
    fun `ddb publish failure fails the main job and reaches the caller`() {
        val job = provider.getJobWithoutSubJobs("DDB")
        val node = mockk<Node>(relaxed = true) { every { remote?.id } returns "remote1" }
        every { mapper.nodeToRenderingJob(any(), "DDB", true) } returns job
        every { jobRepository.save(any()) } answers { firstArg() }
        every { subJobRepository.save(any()) } answers { firstArg() }
        every { jobRepository.failIfActive(job.id, ErrorStrings.ERROR_PROCESSING_JOB) } returns true
        brokerDown()
        val service = DdbJobService(mapper, jobRepository, subJobRepository, publisher)
        service.topicExchangeName = "ex"
        service.jobRoutingKey = "ddb"

        val thrown = runCatching { service.createJob(node, "DDB") }.exceptionOrNull()

        assert(thrown?.message == "broker down")
        verify(exactly = 1) { jobRepository.failIfActive(job.id, ErrorStrings.ERROR_PROCESSING_JOB) }
    }

    @Test
    fun `document publish failure fails only its sub-job and resolves the main job`() {
        val job = provider.prepareJobForConversionModuleTesting("507f191e810c19729de860ea", "DOCUMENT")
        expectSubJobsFailed()
        brokerDown()
        val module = DocumentRenderModule(1L, mapper, mockk(), subJobRepository, publisher, mainJobLogic)
        module.topicExchangeName = "ex"
        module.documentRoutingKey = "doc"

        val thrown = runCatching { module.createConversionSubJobs(job, RenderingJobMessage(job.id.toString())) }.exceptionOrNull()

        assert(thrown?.message == "broker down")
        verify(exactly = 1) { subJobRepository.failIfQueued(job.subJobs.single().id, ErrorStrings.GENERIC_CONVERSION_ERROR) }
        verify(exactly = 1) { mainJobLogic.processMainJob(job.id.toString()) }
        verify(exactly = 0) { jobRepository.failIfActive(any(), any()) }
    }

    @Test
    fun `jupyter publish failure fails only its sub-job and resolves the main job`() {
        val job = provider.prepareJobForConversionModuleTesting("507f191e810c19729de860ea", "JUPYTER")
        expectSubJobsFailed()
        brokerDown()
        val module = JupyterRenderModule(1L, mapper, mockk(), subJobRepository, publisher, mainJobLogic)
        module.topicExchangeName = "ex"
        module.jupyterKey = "jup"

        val thrown = runCatching { module.createConversionSubJobs(job, RenderingJobMessage(job.id.toString())) }.exceptionOrNull()

        assert(thrown?.message == "broker down")
        verify(exactly = 1) { subJobRepository.failIfQueued(job.subJobs.single().id, ErrorStrings.GENERIC_CONVERSION_ERROR) }
        verify(exactly = 1) { mainJobLogic.processMainJob(job.id.toString()) }
    }

    @Test
    fun `image publish failure fails every quality sub-job of the one message`() {
        val job = provider.prepareJobForConversionModuleTesting("507f191e810c19729de860ea", "IMAGE")
        expectSubJobsFailed()
        brokerDown()
        val module = ImageRenderModule(1L, mapper, mockk(), subJobRepository, publisher, mainJobLogic)
        module.topicExchangeName = "ex"
        module.imageRoutingKey = "img"

        val thrown = runCatching {
            module.createConversionSubJobs(job, RenderingJobMessage(job.id.toString(), missingQualities = listOf(100, 200)))
        }.exceptionOrNull()

        assert(thrown?.message == "broker down")
        assert(job.subJobs.size == 2)
        job.subJobs.forEach {
            verify(exactly = 1) { subJobRepository.failIfQueued(it.id, ErrorStrings.GENERIC_CONVERSION_ERROR) }
        }
        verify(exactly = 1) { mainJobLogic.processMainJob(job.id.toString()) }
    }

    @Test
    fun `audio publish failure fails the sub-job of the lost message and rethrows`() {
        val job = provider.prepareJobForConversionModuleTesting("507f191e810c19729de860ea", "AUDIO")
        expectSubJobsFailed()
        brokerDown()
        val module = AudioRenderModule(1L, mapper, mockk(), subJobRepository, publisher, mainJobLogic)
        module.topicExchangeName = "ex"
        module.avRoutingKey = "av"

        val thrown = runCatching {
            module.createConversionSubJobs(job, RenderingJobMessage(job.id.toString(), missingQualities = listOf(1, 2)))
        }.exceptionOrNull()

        assert(thrown?.message == "broker down")
        // the loop stops at the first lost message: only that sub-job exists and is failed
        assert(job.subJobs.size == 1)
        verify(exactly = 1) { subJobRepository.failIfQueued(job.subJobs.single().id, ErrorStrings.GENERIC_CONVERSION_ERROR) }
        verify(exactly = 1) { mainJobLogic.processMainJob(job.id.toString()) }
    }

    @Test
    fun `video publish failure fails the sub-job of the lost message and rethrows`() {
        val job = provider.prepareJobForConversionModuleTesting("507f191e810c19729de860ea", "VIDEO")
        expectSubJobsFailed()
        brokerDown()
        val config = mockk<VideoConverterConfig> { every { getPriority(any(), any()) } returns 1 }
        val module = VideoRenderModule(mapper, mockk(), subJobRepository, publisher, mainJobLogic, config, 1L, "av", "ex")

        val thrown = runCatching {
            module.createConversionSubJobs(job, RenderingJobMessage(job.id.toString(), missingQualities = listOf(720)))
        }.exceptionOrNull()

        assert(thrown?.message == "broker down")
        verify(exactly = 1) { subJobRepository.failIfQueued(job.subJobs.single().id, ErrorStrings.GENERIC_CONVERSION_ERROR) }
        verify(exactly = 1) { mainJobLogic.processMainJob(job.id.toString()) }
    }
}
