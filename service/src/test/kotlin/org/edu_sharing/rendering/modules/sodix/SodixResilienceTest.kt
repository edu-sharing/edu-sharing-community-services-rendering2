package org.edu_sharing.rendering.modules.sodix

import io.mockk.every
import io.mockk.mockk
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.edu_sharing.rendering.core.resilience.RemoteModuleUnavailableException
import org.edu_sharing.rendering.core.resilience.testGuardRegistry
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule

class SodixResilienceTest {
    private lateinit var server: MockWebServer
    private val module = mockk<SodixRenderModule>()
    private val message = SodixJobMessage(id = "1", nodeId = "n", identifier = "ident", role = "student")
    private val ok = """{"playoutUrl":"https://p"}"""

    @BeforeEach
    fun setup() {
        server = MockWebServer().also { it.start() }
        every { module.getCredentials("repo") } returns mapOf("baseurl" to server.url("/").toString())
    }

    @AfterEach
    fun teardown() {
        server.shutdown()
    }

    private fun service(threshold: Int = 5) = SodixApiCallerService(
        WebClient.builder(), JsonMapper.builder().addModule(kotlinModule()).build(),
        testGuardRegistry { circuitBreaker.failureThreshold = threshold }
    )

    @Test
    fun `503 is retried and the later success is returned`() {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setResponseCode(502))
        server.enqueue(MockResponse().setBody(ok))

        assertEquals("https://p", service().getContentUrl(message, module, "repo").first)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `404 is not retried`() {
        server.enqueue(MockResponse().setResponseCode(404))

        assertThrows<WebClientResponseException.NotFound> { service().getContentUrl(message, module, "repo") }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `retries are exhausted after the configured attempts`() {
        repeat(5) { server.enqueue(MockResponse().setResponseCode(503)) }

        assertThrows<WebClientResponseException.ServiceUnavailable> { service().getContentUrl(message, module, "repo") }
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `an open breaker fails the job immediately without contacting sodix`() {
        repeat(20) { server.enqueue(MockResponse().setResponseCode(500)) }
        val service = service(threshold = 2)
        // 2 calls x 3 attempts: the breaker counts per call, so two exhausted calls open it
        repeat(2) { assertThrows<WebClientResponseException> { service.getContentUrl(message, module, "repo") } }
        val before = server.requestCount

        val thrown = assertThrows<RemoteModuleUnavailableException> { service.getContentUrl(message, module, "repo") }

        assertTrue(thrown.message!!.contains("module sodix temporarily unavailable"))
        assertEquals(before, server.requestCount)
    }
}
