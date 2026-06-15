package org.edu_sharing.rendering.edusharingRepo

import org.edu_sharing.generated.repository.backend.services.rest.client.ApiClient
import org.edu_sharing.generated.repository.backend.services.rest.client.api.AuthenticationV1Api
import org.edu_sharing.generated.repository.backend.services.rest.client.api.TrackingV1Api
import org.edu_sharing.rendering.core.annotation.ConditionalOnController
import org.edu_sharing.rendering.utils.SecurityContextUtils
import org.springframework.stereotype.Component

/**
 * Builds repository clients that act on behalf of the current user via the appAuth `EDU-TICKET` flow
 * (the ticket is cached in the rs2 session). Only the controller role acts as a user, so this provider
 * (and its [SessionTicketRepository]) is gated with [ConditionalOnController]; the role-agnostic
 * about/admin factories live in [RestClientProvider]. Add further user-scoped API factories here.
 */
@Component
@ConditionalOnController
class UserBasedRestClientProvider(
    private val sessionTicketRepository: SessionTicketRepository,
    private val authHeaderProvider: AuthHeaderProvider,
) {

    fun getTrackingApiClient(url: String, repoId: String): TrackingV1Api =
        TrackingV1Api(ticketAuthenticatedApiClient(url, repoId))

    /**
     * Builds an [ApiClient] whose requests are authenticated by the [EduSharingTicketAuthInterceptor]:
     * it exchanges the app-signing headers for a user `EDU-TICKET` (cached in the rs2 session) and
     * injects the `Authorization` header. The current user and session are resolved from the security
     * context / request thread. Reuse this to wrap any repository API that must act as the user.
     */
    fun ticketAuthenticatedApiClient(url: String, repoId: String): ApiClient {
        val apiClient = ApiClient()
        apiClient.basePath = "${url}/rest"
        val interceptor = EduSharingTicketAuthInterceptor(
            url = url,
            repoId = repoId,
            userId = SecurityContextUtils.currentUserId(),
            sessionId = SecurityContextUtils.currentSessionId(),
            ticketRepository = sessionTicketRepository,
            authHeaderProvider = authHeaderProvider,
            authenticationApiFactory = ::getAuthenticationApiClient,
        )
        apiClient.httpClient = apiClient.httpClient.newBuilder().addInterceptor(interceptor).build()
        return apiClient
    }

    /**
     * Authentication client for `/authentication/v1/appauth/{userId}`. Built without the
     * [EduSharingTicketAuthInterceptor] so the appAuth call itself does not recurse back into appAuth.
     */
    fun getAuthenticationApiClient(url: String, headers: Map<String, String>): AuthenticationV1Api {
        val apiClient = ApiClient()
        apiClient.basePath = "${url}/rest"
        headers.forEach { (key, value) -> apiClient.addDefaultHeader(key, value) }
        return AuthenticationV1Api(apiClient)
    }
}
