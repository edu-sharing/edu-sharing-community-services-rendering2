package org.edu_sharing.rendering.edusharingRepo

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.edu_sharing.generated.repository.backend.services.rest.client.api.AuthenticationV1Api
import org.edu_sharing.generated.repository.backend.services.rest.client.model.AuthenticationToken
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class EduSharingTicketAuthInterceptorTest {

    private lateinit var server: MockWebServer
    private val ticketRepository = mockk<SessionTicketRepository>(relaxed = true)
    private val authHeaderProvider = mockk<AuthHeaderProvider>()
    private val authApi = mockk<AuthenticationV1Api>()

    private val appHeaders = mapOf(
        "X-Edu-App-Id" to "render",
        "X-Edu-App-Signed" to "render123",
        "X-Edu-App-Sig" to "sig",
        "X-Edu-App-Ts" to "123",
        "X-Edu-App-SignedAlg" to "SHA256withRSA",
    )

    @BeforeEach
    fun setup() {
        server = MockWebServer()
        server.start()
        every { authHeaderProvider.getAuthHeaders(REPO_ID) } returns appHeaders
        every { authApi.authenticate(USER_ID, any()) } returns AuthenticationToken().ticket(FRESH_TICKET)
    }

    @AfterEach
    fun tearDown() = server.shutdown()

    @Test
    fun `authenticates, injects EDU-TICKET and strips app headers when no cached ticket`() {
        every { ticketRepository.getTicket(SESSION_ID, REPO_ID) } returns null
        server.enqueue(MockResponse().setResponseCode(200))

        execute(interceptor(SESSION_ID), trackingRequest())

        val recorded = server.takeRequest()
        assertEquals("EDU-TICKET $FRESH_TICKET", recorded.getHeader("Authorization"))
        EduSharingTicketAuthInterceptor.APP_AUTH_HEADERS.forEach { assertNull(recorded.getHeader(it)) }
        verify(exactly = 1) { authApi.authenticate(USER_ID, any()) }
        verify { ticketRepository.saveTicket(SESSION_ID, REPO_ID, FRESH_TICKET) }
    }

    @Test
    fun `reuses cached ticket without calling appAuth`() {
        every { ticketRepository.getTicket(SESSION_ID, REPO_ID) } returns CACHED_TICKET
        server.enqueue(MockResponse().setResponseCode(200))

        execute(interceptor(SESSION_ID), trackingRequest())

        assertEquals("EDU-TICKET $CACHED_TICKET", server.takeRequest().getHeader("Authorization"))
        verify(exactly = 0) { authApi.authenticate(any(), any()) }
        verify(exactly = 0) { ticketRepository.saveTicket(any(), any(), any()) }
    }

    @Test
    fun `re-authenticates and retries once on 401`() {
        every { ticketRepository.getTicket(SESSION_ID, REPO_ID) } returns CACHED_TICKET
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(200))

        execute(interceptor(SESSION_ID), trackingRequest())

        assertEquals("EDU-TICKET $CACHED_TICKET", server.takeRequest().getHeader("Authorization"))
        assertEquals("EDU-TICKET $FRESH_TICKET", server.takeRequest().getHeader("Authorization"))
        verify { ticketRepository.invalidate(SESSION_ID, REPO_ID) }
        verify(exactly = 1) { authApi.authenticate(USER_ID, any()) }
    }

    @Test
    fun `authenticates without caching when there is no session`() {
        server.enqueue(MockResponse().setResponseCode(200))

        execute(interceptor(sessionId = null), trackingRequest())

        assertEquals("EDU-TICKET $FRESH_TICKET", server.takeRequest().getHeader("Authorization"))
        verify(exactly = 1) { authApi.authenticate(USER_ID, any()) }
        verify(exactly = 0) { ticketRepository.getTicket(any(), any()) }
        verify(exactly = 0) { ticketRepository.saveTicket(any(), any(), any()) }
    }

    private fun interceptor(sessionId: String?) = EduSharingTicketAuthInterceptor(
        url = server.url("/").toString().removeSuffix("/"),
        repoId = REPO_ID,
        userId = USER_ID,
        sessionId = sessionId,
        ticketRepository = ticketRepository,
        authHeaderProvider = authHeaderProvider,
        authenticationApiFactory = { _, _ -> authApi },
    )

    private fun execute(interceptor: EduSharingTicketAuthInterceptor, request: Request) {
        OkHttpClient.Builder().addInterceptor(interceptor).build().newCall(request).execute().use { /* release */ }
    }

    private fun trackingRequest(): Request = Request.Builder()
        .url(server.url("/rest/tracking/v1/tracking/$REPO_ID/VIEW"))
        .apply { appHeaders.forEach { (key, value) -> header(key, value) } }
        .build()

    companion object {
        private const val REPO_ID = "repo-1"
        private const val USER_ID = "user@org"
        private const val SESSION_ID = "session-123"
        private const val CACHED_TICKET = "CACHED-TICKET"
        private const val FRESH_TICKET = "FRESH-TICKET"
    }
}
