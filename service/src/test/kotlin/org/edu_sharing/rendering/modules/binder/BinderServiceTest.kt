package org.edu_sharing.rendering.modules.binder

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.bson.types.ObjectId
import org.edu_sharing.generated.repository.backend.services.rest.client.model.Node
import org.edu_sharing.rendering.core.ErrorStrings.GENERIC_CONVERSION_ERROR
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.modules.binder.git.GitService
import org.edu_sharing.rendering.modules.binder.git.GitServiceRegistry
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.edu_sharing.rendering.renderingJob.entity.SubJobStatus
import org.edu_sharing.rendering.renderingJob.queue.JobMessagePublisher
import org.edu_sharing.rendering.renderingJob.repository.RenderingJobRepository
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.amqp.AmqpException
import org.springframework.amqp.core.AmqpTemplate
import org.springframework.web.reactive.function.client.WebClient

class BinderServiceTest {
    private val mapper = mockk<Mapper>()
    private val jobRepository = mockk<RenderingJobRepository>()
    private val subJobRepository = mockk<SubJobRepository>()
    private val amqpTemplate = mockk<AmqpTemplate>()
    private val jobMessagePublisher = mockk<JobMessagePublisher>()
    private val gitServiceRegistry = mockk<GitServiceRegistry>()
    private val gitService = mockk<GitService>()
    private val module = mockk<BinderRenderModule>()
    private val node = mockk<Node>()

    private val jobId = ObjectId()
    private val job = mockk<RenderingJob>()
    private val savedSubJobs = mutableListOf<SubJob>()

    @BeforeEach
    fun setup() {
        every { node.properties } returns mutableMapOf("ccm:wwwurl" to mutableListOf("https://github.com/u/r"))
        every { node.ref } returns mockk { every { id } returns "node-1" }
        every { gitServiceRegistry.getService(any()) } returns gitService
        every { module.module() } returns "BINDER"
        every { mapper.nodeToRenderingJob(node, "BINDER", true) } returns job
        every { job.id } returns jobId
        every { job.externalUrl } returns "https://github.com/u/r/blob/main/notebook.ipynb"
        every { job.repoId } returns "repo-1"
        every { jobRepository.save(any<RenderingJob>()) } answers { firstArg() }
        every { subJobRepository.save(any<SubJob>()) } answers { firstArg<SubJob>().also { savedSubJobs.add(it) } }
        every { jobMessagePublisher.publishForJob(any(), any(), any(), any()) } returns Unit
        every { amqpTemplate.convertAndSend(any<String>(), any<String>(), any<Any>()) } returns Unit
    }

    private fun newService(withPreview: Boolean): BinderService {
        val service = BinderService(
            mapper = mapper,
            jobRepository = jobRepository,
            subJobRepository = subJobRepository,
            amqpTemplate = amqpTemplate,
            jobMessagePublisher = jobMessagePublisher,
            gitServiceRegistry = gitServiceRegistry,
            jupyterConverterWebClient = if (withPreview) mockk<WebClient>() else null,
        )
        service.topicExchangeName = "exchange"
        service.binderJobRoutingKey = "binder.key"
        service.previewJobRoutingKey = "preview.key"
        if (withPreview) {
            every { gitService.identifyDeepLink(any()) } returns true
            every { module.getCredentials("repo-1") } returns mapOf("githubtoken" to "token")
        }
        return service
    }

    @Test
    fun `publishes the upload message through the job publisher with the main job id`() {
        val result = newService(withPreview = false).createJobs(node, module)

        assertEquals(jobId.toString(), result)
        verify(exactly = 1) { jobMessagePublisher.publishForJob("exchange", "binder.key", any(), jobId) }
        // the upload message must not bypass the compensation
        verify(exactly = 0) { amqpTemplate.convertAndSend(any<String>(), "binder.key", any<Any>()) }
    }

    @Test
    fun `lets a failed upload publish reach the caller`() {
        every { jobMessagePublisher.publishForJob(any(), any(), any(), any()) } throws AmqpException("broker down")

        assertThrows(AmqpException::class.java) { newService(withPreview = false).createJobs(node, module) }
    }

    @Test
    fun `a lost preview message fails only the preview sub-job`() {
        every { amqpTemplate.convertAndSend(any<String>(), any<String>(), any<Any>()) } throws AmqpException("broker down")

        val result = newService(withPreview = true).createJobs(node, module)

        assertEquals(jobId.toString(), result, "the main job is still created")
        verify(exactly = 1) { jobMessagePublisher.publishForJob("exchange", "binder.key", any(), jobId) }
        val preview = savedSubJobs.last { it.additionalData?.get("preview") == "preview" }
        assertEquals(SubJobStatus.FAILED, preview.status)
        assertEquals(GENERIC_CONVERSION_ERROR, preview.errorMessage)
        assertNotNull(preview.finishedDate)
        assertTrue(savedSubJobs.first { it.additionalData == null }.status == SubJobStatus.QUEUED, "upload sub-job untouched")
    }

    @Test
    fun `enqueues the preview message when the broker is up`() {
        newService(withPreview = true).createJobs(node, module)

        verify(exactly = 1) { amqpTemplate.convertAndSend("exchange", "preview.key", any<Any>()) }
        val preview = savedSubJobs.last { it.additionalData?.get("preview") == "preview" }
        assertEquals(SubJobStatus.QUEUED, preview.status)
    }
}
