package org.edu_sharing.rendering.modules.omega

import io.mockk.every
import io.mockk.mockk
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.web.reactive.function.client.WebClient
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule

class OmegaApiCallerServiceTest {
    private lateinit var server: MockWebServer
    private lateinit var underTest: OmegaApiCallerService
    private val module = mockk<OmegaRenderModule>()
    private val message = OmegaJobMessage(id = "1", nodeId = "n", identifier = "ident", role = "student")

    @BeforeEach
    fun setup() {
        server = MockWebServer()
        server.start()
        every { module.getCredentials("repo") } returns mapOf("baseurl" to server.url("/api").toString())
        underTest = OmegaApiCallerService(
            WebClient.builder(), WebClient.builder(),
            JsonMapper.builder().addModule(kotlinModule()).build()
        )
    }

    @AfterEach
    fun teardown() {
        server.shutdown()
    }

    private fun apiAnswer(streamPath: String = "/file") = MockResponse()
        .setHeader("Content-Type", "application/json")
        .setBody("""{"get":{"identifier":"ident","streamURL":"${server.url(streamPath)}"}}""")

    @Test
    fun `HTML instead of JSON gives a readable error without decoding exception`() {
        server.enqueue(MockResponse().setHeader("Content-Type", "text/html").setBody("<html>Maintenance</html>"))

        val exception = assertThrows<IllegalStateException> { underTest.getContentUrl(message, module, "repo") }

        assertTrue(exception.message!!.contains("Omega API"))
        assertTrue(exception.message!!.contains("<html>Maintenance</html>"))
    }

    @Test
    fun `error object without get node gives a readable error`() {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"error":"bad token"}"""))

        val exception = assertThrows<IllegalStateException> { underTest.getContentUrl(message, module, "repo") }

        assertTrue(exception.message!!.contains("bad token"))
    }

    @Test
    fun `validation falls back to GET Range when HEAD is rejected with 405`() {
        server.enqueue(apiAnswer())
        server.enqueue(MockResponse().setResponseCode(405))
        server.enqueue(MockResponse().setResponseCode(206))

        val (streamUrl, _) = underTest.getContentUrl(message, module, "repo")

        assertTrue(streamUrl.endsWith("/file"))
        server.takeRequest()
        assertEquals("HEAD", server.takeRequest().method)
        val probe = server.takeRequest()
        assertEquals("GET", probe.method)
        assertEquals("bytes=0-0", probe.getHeader("Range"))
    }

    @Test
    fun `validation fails when the GET Range fallback is also rejected`() {
        server.enqueue(apiAnswer())
        server.enqueue(MockResponse().setResponseCode(403))
        server.enqueue(MockResponse().setResponseCode(404))

        val exception = assertThrows<Exception> { underTest.getContentUrl(message, module, "repo") }

        assertTrue(exception.message!!.contains("status 404"))
    }

    @Test
    fun `successful HEAD does not trigger the fallback`() {
        server.enqueue(apiAnswer())
        server.enqueue(MockResponse().setResponseCode(200))

        underTest.getContentUrl(message, module, "repo")

        assertEquals(2, server.requestCount)
    }
}
