package org.edu_sharing.rendering.modules.moodle

import io.mockk.mockk
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.web.reactive.function.client.WebClient
import java.time.Duration

/** Token retrieval must survive Moodle's error payloads and hangs and must never leak the credentials. */
class MoodleRenderModuleTokenTest {

    private lateinit var server: MockWebServer
    private lateinit var webClient: WebClient
    private lateinit var underTest: MoodleRenderModule

    @BeforeEach
    fun setup() {
        server = MockWebServer()
        server.start()
        webClient = WebClient.builder().baseUrl(server.url("/").toString().trimEnd('/')).build()
        underTest = MoodleRenderModule(1L, mockk(relaxed = true), mockk(relaxed = true))
    }

    @AfterEach
    fun tearDown() = server.shutdown()

    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    @Test
    fun `returns the token`() {
        server.enqueue(json("""{"token":"abc123","privatetoken":null}"""))

        assertEquals("abc123", underTest.getWebserviceToken(webClient, "u", "pw"))
    }

    @Test
    fun `passes the Moodle error through`() {
        server.enqueue(json("""{"error":"Invalid login, please try again","errorcode":"invalidlogin"}"""))

        val e = assertThrows<IllegalStateException> { underTest.getWebserviceToken(webClient, "u", "s3cret") }

        assertTrue(e.message!!.contains("Invalid login, please try again"))
        assertTrue(e.message!!.contains("invalidlogin"))
        assertFalse(e.message!!.contains("s3cret"))
    }

    @Test
    fun `tolerates an HTML response`() {
        server.enqueue(MockResponse().setHeader("Content-Type", "text/html").setBody("<html><body>Maintenance</body></html>"))

        val e = assertThrows<IllegalStateException> { underTest.getWebserviceToken(webClient, "u", "pw") }

        assertTrue(e.message!!.contains("non-JSON"))
        assertFalse(e.message!!.contains("Maintenance"))
    }

    @Test
    fun `http error does not leak query string`() {
        server.enqueue(MockResponse().setResponseCode(503))

        val e = assertThrows<IllegalStateException> { underTest.getWebserviceToken(webClient, "user1", "s3cret") }

        assertTrue(e.message!!.contains("503"))
        assertFalse(e.message!!.contains("s3cret"))
        assertFalse(e.message!!.contains("password"))
        assertNull(e.cause)
    }

    @Test
    fun `times out instead of blocking forever`() {
        server.enqueue(MockResponse().setBody("{}").setBodyDelay(10, java.util.concurrent.TimeUnit.SECONDS)
            .setHeader("Content-Type", "application/json"))

        val start = System.nanoTime()
        val e = assertThrows<IllegalStateException> {
            underTest.getWebserviceToken(webClient, "u", "s3cret", Duration.ofMillis(300))
        }

        assertTrue(Duration.ofNanos(System.nanoTime() - start).seconds < 5)
        assertTrue(e.message!!.contains("timed out"))
        assertFalse(e.message!!.contains("s3cret"))
    }
}
