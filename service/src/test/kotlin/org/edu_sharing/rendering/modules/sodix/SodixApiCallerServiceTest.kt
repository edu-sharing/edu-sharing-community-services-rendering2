package org.edu_sharing.rendering.modules.sodix

import org.edu_sharing.rendering.core.resilience.testGuardRegistry
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

class SodixApiCallerServiceTest {
    private lateinit var server: MockWebServer
    private lateinit var underTest: SodixApiCallerService
    private val module = mockk<SodixRenderModule>()
    private val message = SodixJobMessage(id = "1", nodeId = "n", identifier = "ident", role = "student")

    @BeforeEach
    fun setup() {
        server = MockWebServer()
        server.start()
        every { module.getCredentials("repo") } returns mapOf("baseurl" to server.url("/").toString())
        underTest = SodixApiCallerService(WebClient.builder(), JsonMapper.builder().addModule(kotlinModule()).build(), testGuardRegistry())
    }

    @AfterEach
    fun teardown() {
        server.shutdown()
    }

    @Test
    fun `valid answer is mapped even with a wrong content type`() {
        server.enqueue(
            MockResponse().setHeader("Content-Type", "text/plain")
                .setBody("""{"playoutUrl":"https://p","downloadUrl":"https://d"}""")
        )

        assertEquals(Pair("https://p", "https://d"), underTest.getContentUrl(message, module, "repo"))
    }

    @Test
    fun `HTML error page gives a readable error`() {
        server.enqueue(MockResponse().setHeader("Content-Type", "text/html").setBody("<html>502</html>"))

        val exception = assertThrows<IllegalStateException> { underTest.getContentUrl(message, module, "repo") }

        assertTrue(exception.message!!.contains("Sodix API"))
        assertTrue(exception.message!!.contains("<html>502</html>"))
    }

    @Test
    fun `JSON error object without playoutUrl gives a readable error`() {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"error":"nope"}"""))

        val exception = assertThrows<IllegalStateException> { underTest.getContentUrl(message, module, "repo") }

        assertTrue(exception.message!!.contains("nope"))
    }

    @Test
    fun `empty body gives a readable error`() {
        server.enqueue(MockResponse().setResponseCode(200))

        val exception = assertThrows<IllegalStateException> { underTest.getContentUrl(message, module, "repo") }

        assertTrue(exception.message!!.contains("empty response"))
    }
}
