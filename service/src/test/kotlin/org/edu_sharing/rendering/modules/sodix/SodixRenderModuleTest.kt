package org.edu_sharing.rendering.modules.sodix

import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import org.edu_sharing.generated.repository.backend.services.rest.client.model.Node
import org.edu_sharing.generated.repository.backend.services.rest.client.model.NodeRef
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.edusharingRepo.services.RepositoryRegistrationStorageService
import org.edu_sharing.rendering.renderingJob.entity.RenderingJobStatus
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.edu_sharing.rendering.renderingJob.entity.SubJobStatus
import org.edu_sharing.rendering.renderingJob.repository.RenderingJobRepository
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.edu_sharing.rendering.testUtils.JobDataProvider
import org.junit.jupiter.api.Test
import org.springframework.amqp.core.AmqpTemplate

class SodixRenderModuleTest {
    private val repositoryRegistrationStorageService = mockk<RepositoryRegistrationStorageService>()
    private val mapper = mockk<Mapper>()
    private val jobRepository = mockk<RenderingJobRepository>(relaxUnitFun = true)
    private val amqpTemplate = mockk<AmqpTemplate>(relaxUnitFun = true)
    private val subJobRepository = mockk<SubJobRepository>(relaxed = true)
    private val jobDataProvider = JobDataProvider()

    private val exchange = "topic-exchange"
    private val routingKey = "sodix.key"
    private val nodePermissionExpirationTime = 7200L

    private val underTest = spyk(
        SodixRenderModule(
            repositoryRegistrationStorageService,
            mapper,
            jobRepository,
            amqpTemplate,
            subJobRepository,
            exchange,
            routingKey,
            nodePermissionExpirationTime
        )
    )

    private fun node(
        editorialState: String? = null,
        mimetype: String = "video/mp4",
    ): Node {
        val props = mutableMapOf(
            "ccm:replicationsource" to listOf("sodix"),
            "ccm:replicationsourceid" to listOf("sodix-123"),
        )
        editorialState?.let { props["ccm:editorial_state"] = listOf(it) }
        return Node().ref(NodeRef().id("n1").repo("repoid")).mimetype(mimetype).properties(props)
    }

    // --- config wiring -----------------------------------------------------

    @Test
    fun exposesConfiguredNodePermissionExpirationTime() {
        assert(underTest.getNodePermissionExpirationTime() == nodePermissionExpirationTime)
    }

    // --- deferral decision -------------------------------------------------

    @Test
    fun rendersDeferredForPaidMedia() {
        // playable mimetype, but paid media -> still deferred (never embedded)
        every { underTest.getCredentials("repoid") } returns mapOf("playoutMimetypes" to "video")
        assert(underTest.rendersDeferred(node(editorialState = "restricted_mz")))
    }

    @Test
    fun rendersDeferredForNonPlayableMimetype() {
        every { underTest.getCredentials("repoid") } returns mapOf("playoutMimetypes" to "video/mp4")
        assert(underTest.rendersDeferred(node(mimetype = "application/pdf")))
    }

    @Test
    fun doesNotDeferPlayableNonPaidMedia() {
        every { underTest.getCredentials("repoid") } returns mapOf("playoutMimetypes" to "video")
        assert(!underTest.rendersDeferred(node(mimetype = "video/mp4")))
    }

    // --- handle() vs fetchOnDemand() ---------------------------------------

    @Test
    fun handleReturnsDeferredWithoutFetchingWhenDeferred() {
        val node = node()
        every { underTest.rendersDeferred(node) } returns true

        val response = underTest.handle(node)

        assert(response.deferred)
        assert(response.jobId == null)
        assert(response.module == "SODIX")
        verify(exactly = 0) { jobRepository.save(any()) }
        verify(exactly = 0) { amqpTemplate.convertAndSend(any<String>(), any<String>(), any<Any>()) }
    }

    @Test
    fun handleFetchesWhenNotDeferred() {
        val node = node()
        val job = jobDataProvider.getJobWithoutSubJobs(module = "SODIX")
        every { underTest.rendersDeferred(node) } returns false
        every { mapper.nodeToRenderingJob(node, "SODIX", true) } returns job
        every { jobRepository.save(job) } returns job
        every { subJobRepository.save(any<SubJob>()) } answers { firstArg() }

        val response = underTest.handle(node)

        assert(!response.deferred)
        assert(response.jobId == job.id.toString())
        verify(exactly = 1) { jobRepository.save(job) }
        verify(exactly = 1) {
            amqpTemplate.convertAndSend(exchange, routingKey, match<SodixJobMessage> {
                it.identifier == "sodix-123" && !it.isPaidMedia
            })
        }
    }

    // --- refresh (existing behavior) ---------------------------------------

    private fun sodixJob(subJobStatus: SubJobStatus) =
        jobDataProvider.getJobWithoutSubJobs(module = "SODIX").apply {
            renderParams = mapOf("identifier" to "sodix-123", "isPaidMedia" to "false")
            subJobs = mutableListOf(
                jobDataProvider.getDummySubJob(
                    subId = JobDataProvider.SUB_ID_1,
                    status = subJobStatus,
                    module = "SODIX",
                    mimeType = "video/mp4",
                    quality = 0
                )
            )
        }

    @Test
    fun refreshLinksReEnqueuesWhenClaimIsWon() {
        val job = sodixJob(SubJobStatus.FINISHED)
        every { subJobRepository.claimForRefresh(job.subJobs.first().id) } returns true

        underTest.refreshLinks(job)

        verify(exactly = 1) {
            jobRepository.updateStatusWithoutVersion(job.id, RenderingJobStatus.PROCESSING)
        }
        verify(exactly = 1) {
            amqpTemplate.convertAndSend(exchange, routingKey, match<SodixJobMessage> {
                it.identifier == "sodix-123" && !it.isPaidMedia && it.nodeId == job.esObjectId
            })
        }
    }

    @Test
    fun refreshLinksDoesNotReEnqueueWhenClaimIsLost() {
        val job = sodixJob(SubJobStatus.FINISHED)
        every { subJobRepository.claimForRefresh(job.subJobs.first().id) } returns false

        underTest.refreshLinks(job)

        verify(exactly = 1) { subJobRepository.claimForRefresh(job.subJobs.first().id) }
        verify(exactly = 0) { amqpTemplate.convertAndSend(any<String>(), any<String>(), any<Any>()) }
    }
}
