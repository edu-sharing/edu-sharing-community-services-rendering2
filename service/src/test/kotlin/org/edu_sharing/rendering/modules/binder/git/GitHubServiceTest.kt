package org.edu_sharing.rendering.modules.binder.git

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.edu_sharing.rendering.modules.binder.dto.GitDetails
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.util.unit.DataSize
import org.springframework.web.reactive.function.client.WebClient

class GitHubServiceTest {
    private lateinit var server: MockWebServer
    private val details = GitDetails(user = "u", repo = "r", branch = "main", filePath = "nb/test.ipynb")

    @BeforeEach
    fun setup() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun teardown() {
        server.shutdown()
    }

    private fun service(maxFileSize: DataSize = DataSize.ofMegabytes(5)): GitHubService {
        // Deliberately tiny codec limit: the file download must not be bound by it.
        val client = WebClient.builder()
            .baseUrl(server.url("/").toString())
            .codecs { it.defaultCodecs().maxInMemorySize(1024) }
            .build()
        return GitHubService(client, client, maxFileSize)
    }

    @Test
    fun `file larger than the codec buffer limit is downloaded`() {
        val content = "x".repeat(200_000)
        server.enqueue(MockResponse().setBody(content).setResponseCode(200))

        val result = service().getFile(details, "tok").readAllBytes()

        assertEquals(content.length, result.size)
        assertEquals("Bearer tok", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `file above the configured limit is rejected with a readable message`() {
        server.enqueue(MockResponse().setBody("x".repeat(50_000)).setResponseCode(200))

        val exception = assertThrows<IllegalStateException> {
            service(DataSize.ofKilobytes(10)).getFile(details, "tok")
        }

        assertTrue(exception.message!!.contains("exceeds the limit"))
    }

    @Test
    fun `rate limit on file download is reported readable`() {
        server.enqueue(
            MockResponse().setResponseCode(403).setHeader("X-RateLimit-Remaining", "0")
                .setHeader("X-RateLimit-Reset", "1700000000")
        )

        val exception = assertThrows<IllegalStateException> { service().getFile(details, "tok") }

        assertTrue(exception.message!!.contains("rate limit"), exception.message)
    }

    @Test
    fun `429 on file download is reported as rate limit`() {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "30"))

        val exception = assertThrows<IllegalStateException> { service().getFile(details, "tok") }

        assertTrue(exception.message!!.contains("retry-after=30"), exception.message)
    }

    @Test
    fun `freshness check treats 403 and 429 as up to date`() {
        server.enqueue(MockResponse().setResponseCode(403))
        server.enqueue(MockResponse().setResponseCode(429))

        assertTrue(service().checkIfObjectLinkIsUpToDate(0, details, "tok"))
        assertTrue(service().checkIfObjectLinkIsUpToDate(0, details, "tok"))
    }

    @Test
    fun `freshness check compares the latest commit date`() {
        val body = """[{"commit":{"committer":{"date":"2020-01-01T00:00:00Z"}}}]"""
        server.enqueue(MockResponse().setBody(body).setHeader("Content-Type", "application/json"))
        server.enqueue(MockResponse().setBody(body).setHeader("Content-Type", "application/json"))

        // commit (2020) is older than the cache entry -> up to date
        assertTrue(service().checkIfObjectLinkIsUpToDate(Long.MAX_VALUE / 1000, details, "tok"))
        // commit is newer than the cache entry -> outdated
        assertFalse(service().checkIfObjectLinkIsUpToDate(1, details, "tok"))
        assertTrue(server.takeRequest().path!!.contains("per_page=1"))
    }
}
