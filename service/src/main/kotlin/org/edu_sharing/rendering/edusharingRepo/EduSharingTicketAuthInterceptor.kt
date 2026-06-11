package org.edu_sharing.rendering.edusharingRepo

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.edu_sharing.generated.repository.backend.services.rest.client.ApiException
import org.edu_sharing.generated.repository.backend.services.rest.client.api.AuthenticationV1Api
import org.edu_sharing.generated.repository.backend.services.rest.client.model.UserProfileAppAuth
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.HttpURLConnection

/**
 * OkHttp filter that authenticates outbound repository requests with an `EDU-TICKET` obtained from
 * `/authentication/v1/appauth/{userId}` (the app authenticates with its signing headers and receives
 * a user ticket). The ticket is cached in the rs2 session via [SessionTicketRepository] and reused
 * across requests; when the repository session behind it expires the request comes back `401`, which
 * triggers a fresh appAuth and a single retry.
 *
 * The app-signing headers ([AuthHeaderProvider]) are minted fresh for the appAuth call only and are
 * never sent on the proceeded request — they are stripped and replaced by the
 * `Authorization: EDU-TICKET <ticket>` header.
 *
 * The instance is built per client by [RestClientProvider] with the per-call context (url/repo/user/
 * session); the appAuth client is created through [authenticationApiFactory] **without** this
 * interceptor to avoid recursion.
 */
class EduSharingTicketAuthInterceptor(
    private val url: String,
    private val repoId: String,
    private val userId: String,
    private val sessionId: String?,
    private val ticketRepository: SessionTicketRepository,
    private val authHeaderProvider: AuthHeaderProvider,
    private val authenticationApiFactory: (url: String, headers: Map<String, String>) -> AuthenticationV1Api,
) : Interceptor {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun intercept(chain: Interceptor.Chain): Response {
        var ticket = cachedTicket() ?: authenticateAndStore()
        var response = chain.proceed(withTicket(chain.request(), ticket))

        if (response.code == HttpURLConnection.HTTP_UNAUTHORIZED) {
            log.debug("Tracking request to {} was unauthorized for repo {}; re-authenticating", url, repoId)
            response.close()
            sessionId?.let { ticketRepository.invalidate(it, repoId) }
            ticket = authenticateAndStore()
            response = chain.proceed(withTicket(chain.request(), ticket))
        }
        return response
    }

    private fun cachedTicket(): String? = sessionId?.let { ticketRepository.getTicket(it, repoId) }

    private fun authenticateAndStore(): String {
        val headers = authHeaderProvider.getAuthHeaders(repoId)
        val ticket = try {
            authenticationApiFactory(url, headers).authenticate(userId, UserProfileAppAuth()).ticket
        } catch (e: ApiException) {
            throw IOException("appAuth against $url failed for user $userId", e)
        } ?: throw IOException("appAuth against $url returned no ticket for user $userId")
        sessionId?.let { ticketRepository.saveTicket(it, repoId, ticket) }
        return ticket
    }

    private fun withTicket(request: Request, ticket: String): Request =
        request.newBuilder().apply {
            APP_AUTH_HEADERS.forEach { removeHeader(it) }
            header(AUTHORIZATION_HEADER, "$EDU_TICKET_SCHEME $ticket")
        }.build()

    companion object {
        private const val AUTHORIZATION_HEADER = "Authorization"
        private const val EDU_TICKET_SCHEME = "EDU-TICKET"

        /** App-signing headers produced by [AuthHeaderProvider]; consumed by appAuth, never forwarded. */
        val APP_AUTH_HEADERS = listOf(
            "X-Edu-App-Id",
            "X-Edu-App-Signed",
            "X-Edu-App-Sig",
            "X-Edu-App-Ts",
            "X-Edu-App-SignedAlg",
        )
    }
}
