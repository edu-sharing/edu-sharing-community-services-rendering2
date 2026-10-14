package org.edu_sharing.rendering.modules.moodle

import io.mockk.mockk
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType

/**
 * The ping on registration has to reach Moodle the same way the upload does: with baseurl pointing
 * into the docker network, Moodle only answers with JSON when the Host header matches its wwwroot -
 * otherwise it sends a redirect page.
 */
class MoodleRenderModulePingTest {

    private lateinit var mockServer: MockWebServer
    private lateinit var underTest: MoodleRenderModule

    @BeforeEach
    fun setup() {
        mockServer = MockWebServer()
        mockServer.start()
        underTest = MoodleRenderModule(55L, mockk(), mockk())
    }

    @AfterEach
    fun tearDown() = mockServer.shutdown()

    private fun credentials(vararg extra: Pair<String, String>) = mapOf(
        "baseurl" to mockServer.url("/").toString(),
        "token" to "secrettoken",
        "timeout" to "30",
        "categoryid" to "1",
        "password" to "renderer"
    ) + mapOf(*extra)

    private fun pingOk() =
        mockServer.enqueue(MockResponse().setBody("1").setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE))

    @Test
    fun `the ping sends publicurl's host as Host header`() {
        pingOk()

        underTest.validateThirdPartyCredentials(credentials("publicurl" to "http://moodle.example.org:11111"), "repo1")

        assertEquals("moodle.example.org:11111", mockServer.takeRequest().getHeader("Host"))
    }

    @Test
    fun `without publicurl the ping keeps baseurl's host`() {
        pingOk()

        underTest.validateThirdPartyCredentials(credentials(), "repo1")

        assertEquals("${mockServer.hostName}:${mockServer.port}", mockServer.takeRequest().getHeader("Host"))
    }
}
