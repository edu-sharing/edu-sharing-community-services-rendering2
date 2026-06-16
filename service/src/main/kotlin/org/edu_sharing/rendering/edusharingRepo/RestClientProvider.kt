package org.edu_sharing.rendering.edusharingRepo

import org.edu_sharing.generated.repository.backend.services.rest.client.ApiClient
import org.edu_sharing.generated.repository.backend.services.rest.client.api.AboutApi
import org.edu_sharing.generated.repository.backend.services.rest.client.api.AdminV1Api
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class RestClientProvider(
    private val tracePropagatingInterceptor: TracePropagatingInterceptor
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun getAboutApiClient(url: String): AboutApi {
        log.debug("Creating AboutApi client for base URL: $url")
        val apiClient = ApiClient()
        apiClient.basePath = "${url}/rest"
        apiClient.httpClient = apiClient.httpClient.newBuilder().addInterceptor(tracePropagatingInterceptor).build()
        return AboutApi(apiClient)
    }

    fun getAdminV1Client(url: String, username: String, password: String): AdminV1Api {
        log.debug("Creating AdminV1Api client for base URL: $url (username=$username)")
        val apiClient = ApiClient()
        apiClient.basePath = "${url}/rest"
        apiClient.setUsername(username)
        apiClient.setPassword(password)
        apiClient.httpClient = apiClient.httpClient.newBuilder().addInterceptor(tracePropagatingInterceptor).build()
        val adminV1Api = AdminV1Api(apiClient)
        return adminV1Api
    }
}
