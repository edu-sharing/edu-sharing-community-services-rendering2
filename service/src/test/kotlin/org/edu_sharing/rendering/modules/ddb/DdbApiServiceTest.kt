package org.edu_sharing.rendering.modules.ddb

import io.mockk.every
import io.mockk.mockk
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.edu_sharing.rendering.modules.ModuleRegistry
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.edu_sharing.rendering.renderingJob.entity.SubJobStatus
import org.edu_sharing.rendering.renderingJob.repository.RenderingJobRepository
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient

class DdbApiServiceTest {
    private lateinit var restServer: MockWebServer
    private lateinit var iiifServer: MockWebServer
    private val subJobRepository = mockk<SubJobRepository>()
    private val module = mockk<DdbRenderModule>()
    private val registry = mockk<ModuleRegistry>()
    private lateinit var underTest: DdbApiService
    private lateinit var job: RenderingJob
    private lateinit var subJob: SubJob

    @BeforeEach
    fun setup() {
        restServer = MockWebServer().also { it.start() }
        iiifServer = MockWebServer().also { it.start() }
        val properties = DdbApiProperties().apply {
            restApiBaseUrl = restServer.url("/").toString().removeSuffix("/")
            iiifApiBaseUrl = iiifServer.url("/").toString().removeSuffix("/")
        }
        every { registry.getRenderModule<DdbRenderModule>("ddb") } returns module
        every { module.getCredentials("repo") } returns mapOf("apiToken" to "SECRETKEY")
        every { subJobRepository.save(any<SubJob>()) } answers { firstArg() }
        job = RenderingJob(
            module = "ddb", esObjectType = "t", esObjectId = "n", repoId = "repo",
            esHash = "h", mimeType = "m", nodeVersion = "1"
        )
        subJob = SubJob(routingKey = "ddb", parent = job)
        job.subJobs = mutableListOf(subJob)
        underTest = DdbApiService(
            renderingJobRepository = mockk<RenderingJobRepository>(relaxed = true),
            moduleRegistry = registry,
            subJobRepository = subJobRepository,
            webClientBuilder = WebClient.builder(),
            ddbApiProperties = properties
        )
    }

    @AfterEach
    fun teardown() {
        restServer.shutdown()
        iiifServer.shutdown()
    }

    @Test
    fun `REST call sends the consumer key as header and not in the url`() {
        restServer.enqueue(
            MockResponse().setBody(
                """{"binaries":{"binary":{"@ref":"abc","@kind":"k","@license_group":"g"}},
                    "view":{"item":{"institution":{"name":"inst"}}}}"""
            )
        )
        iiifServer.enqueue(MockResponse().setBody("""{"sizes":[{"width":100,"height":50}]}"""))

        underTest.process("remote1", job)

        assertEquals(SubJobStatus.FINISHED, subJob.status)
        val rest = restServer.takeRequest()
        assertEquals("OAuth oauth_consumer_key=\"SECRETKEY\"", rest.getHeader("Authorization"))
        assertFalse(rest.path!!.contains("SECRETKEY"))
        assertNull(rest.requestUrl!!.queryParameter("oauth_consumer_key"))
    }

    @Test
    fun `HTML answer fails the sub-job`() {
        restServer.enqueue(MockResponse().setBody("<html>Down for maintenance</html>"))

        underTest.process("remote1", job)

        assertEquals(SubJobStatus.FAILED, subJob.status)
        assertTrue(subJob.errorMessage != null)
    }
}
