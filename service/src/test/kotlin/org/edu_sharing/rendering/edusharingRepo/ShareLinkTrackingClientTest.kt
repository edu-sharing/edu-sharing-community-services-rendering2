package org.edu_sharing.rendering.edusharingRepo

import io.mockk.every
import io.mockk.mockk
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class ShareLinkTrackingClientTest {

    private lateinit var server: MockWebServer
    private val restClientProvider = mockk<RestClientProvider>()
    private val authHeaderProvider = mockk<AuthHeaderProvider>()

    private val appHeaders = mapOf(
        "X-Edu-App-Id" to "render",
        "X-Edu-App-Signed" to "render123",
        "X-Edu-App-Sig" to "sig",
        "X-Edu-App-Ts" to "123",
        "X-Edu-App-SignedAlg" to "SHA256withRSA",
    )

    private lateinit var underTest: ShareLinkTrackingClient

    @BeforeEach
    fun setup() {
        server = MockWebServer()
        server.start()
        every { restClientProvider.getSignedHttpClient() } returns OkHttpClient()
        every { authHeaderProvider.getAuthHeaders("repo-1") } returns appHeaders
        underTest = ShareLinkTrackingClient(restClientProvider, authHeaderProvider)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun trackSendsSignedPutToTheShareLinkEndpointWithoutTicket() {
        // Arrange
        server.enqueue(MockResponse().setResponseCode(200))
        val repositoryUrl = server.url("/edu-sharing").toString().trimEnd('/')

        // Act
        underTest.track(repositoryUrl, "repo-1", "VIEW_MATERIAL", "node-1")

        // Assert
        val request = server.takeRequest(5, TimeUnit.SECONDS)
        assertNotNull(request)
        assertEquals("PUT", request!!.method)
        assertEquals("/edu-sharing/rest/tracking/v1/tracking/repo-1/VIEW_MATERIAL/share-link?node=node-1", request.path)
        appHeaders.forEach { (name, value) -> assertEquals(value, request.getHeader(name)) }
        assertEquals(null, request.getHeader("Authorization"))
    }

    @Test
    fun trackDoesNotThrowIfTheRepositoryRejectsTheRequest() {
        // Arrange
        server.enqueue(MockResponse().setResponseCode(403))
        val repositoryUrl = server.url("/edu-sharing").toString().trimEnd('/')

        // Act
        underTest.track(repositoryUrl, "repo-1", "VIEW_MATERIAL", "node-1")

        // Assert
        assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
    }
}
