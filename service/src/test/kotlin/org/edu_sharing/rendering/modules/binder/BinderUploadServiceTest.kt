package org.edu_sharing.rendering.modules.binder

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.bson.types.ObjectId
import org.edu_sharing.rendering.modules.ModuleRegistry
import org.edu_sharing.rendering.modules.binder.dto.BinderSseEvent
import org.edu_sharing.rendering.modules.binder.dto.GitDetails
import org.edu_sharing.rendering.modules.binder.git.GitServiceRegistry
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.edu_sharing.rendering.renderingJob.entity.SubJobStatus
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.codec.ServerSentEvent
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import java.io.IOException
import java.time.Duration

class BinderUploadServiceTest {
    private val subJobRepository = mockk<SubJobRepository>()
    private val mainJobLogic = mockk<BinderMainJobLogic>(relaxed = true)
    private val parent = mockk<RenderingJob>()
    private val parentId = ObjectId()
    private val gitDetails = GitDetails(user = "u", repo = "r", branch = "main")

    private lateinit var subJob: SubJob
    private lateinit var underTest: BinderUploadService

    @BeforeEach
    fun setup() {
        every { parent.id } returns parentId
        subJob = SubJob(routingKey = "binder", parent = parent, status = SubJobStatus.PROCESSING)
        every { subJobRepository.findByIdOrNull(subJob.id) } answers { subJob }
        every { subJobRepository.save(any<SubJob>()) } answers { firstArg() }
        underTest = newService(Duration.ofSeconds(30))
    }

    private fun newService(idleTimeout: Duration) = BinderUploadService(
        subJobRepository = subJobRepository,
        mainJobLogic = mainJobLogic,
        moduleRegistry = mockk<ModuleRegistry>(),
        gitServiceRegistry = mockk<GitServiceRegistry>(),
        webClientBuilder = WebClient.builder(),
        sseIdleTimeout = idleTimeout
    )

    private fun event(phase: String, url: String? = null, token: String? = null) =
        ServerSentEvent.builder(BinderSseEvent(message = "m", phase = phase, url = url, token = token)).build()

    @Test
    fun `error in the event stream fails the sub-job and re-evaluates the main job`() {
        underTest.consumeEvents(
            Flux.concat(Flux.just(event("building")), Flux.error(IOException("connection reset ?token=SECRET"))),
            subJob.id, gitDetails
        )

        assertEquals(SubJobStatus.FAILED, subJob.status)
        assertTrue(subJob.message!!.startsWith("Error receiving SSE"))
        assertTrue(!subJob.message!!.contains("SECRET"))
        verify(exactly = 1) { mainJobLogic.processMainJob(parentId.toString()) }
    }

    @Test
    fun `stream completing without a terminal status fails the sub-job`() {
        underTest.consumeEvents(Flux.just(event("building"), event("pushing")), subJob.id, gitDetails)

        assertEquals(SubJobStatus.FAILED, subJob.status)
        assertTrue(subJob.message!!.contains("without a final status"))
        verify(exactly = 1) { mainJobLogic.processMainJob(parentId.toString()) }
    }

    @Test
    fun `stream completing after READY keeps the finished sub-job untouched`() {
        underTest.consumeEvents(
            Flux.just(event("ready", url = "https://binder.example/user/x", token = "tok")),
            subJob.id, gitDetails
        )

        assertEquals(SubJobStatus.FINISHED, subJob.status)
        assertEquals("https://binder.example/user/x?token=tok", subJob.message)
        // Only the READY handling re-evaluates the main job, not the completion handler.
        verify(exactly = 1) { mainJobLogic.processMainJob(parentId.toString()) }
    }

    @Test
    fun `failed phase finishes the sub-job as failed exactly once`() {
        underTest.consumeEvents(Flux.just(event("failed")), subJob.id, gitDetails)

        assertEquals(SubJobStatus.FAILED, subJob.status)
        verify(exactly = 1) { mainJobLogic.processMainJob(parentId.toString()) }
    }

    @Test
    fun `idle stream times out and fails the sub-job`() {
        val sink = Sinks.many().multicast().onBackpressureBuffer<ServerSentEvent<BinderSseEvent>>()
        newService(Duration.ofMillis(100)).consumeEvents(sink.asFlux(), subJob.id, gitDetails)

        Thread.sleep(500)

        assertEquals(SubJobStatus.FAILED, subJob.status)
        assertTrue(subJob.message!!.contains("no event within"))
        verify(exactly = 1) { mainJobLogic.processMainJob(parentId.toString()) }
    }

    @Test
    fun `failure in event handling is finalized as failed`() {
        every { subJobRepository.save(any<SubJob>()) } throws IllegalStateException("mongo down") andThenAnswer { firstArg() }

        underTest.consumeEvents(Flux.just(event("building")), subJob.id, gitDetails)

        assertEquals(SubJobStatus.FAILED, subJob.status)
        verify(exactly = 1) { mainJobLogic.processMainJob(parentId.toString()) }
    }
}
