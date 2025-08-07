package org.edu_sharing.rendering.edusharingRepo

import org.edu_sharing.generated.repository.backend.services.rest.client.ApiClient
import org.edu_sharing.generated.repository.backend.services.rest.client.api.AboutApi
import org.edu_sharing.generated.repository.backend.services.rest.client.api.AdminV1Api
import org.edu_sharing.generated.repository.backend.services.rest.client.api.TrackingV1Api
import org.springframework.stereotype.Component

@Component
class RestClientProvider {

    fun getAboutApiClient(url: String): AboutApi {
        val apiClient = ApiClient()
        apiClient.basePath = "${url}/rest"
        return AboutApi(apiClient)
    }

    fun getAdminV1Client(url: String, username: String, password: String): AdminV1Api {
        val apiClient = ApiClient()
        apiClient.setBasePath("${url}/rest")
        apiClient.setUsername(username)
        apiClient.setPassword(password)
        val adminV1Api = AdminV1Api(apiClient)
        return adminV1Api
    }

    fun getTrackingApiClient(url: String, headers: Map<String, String> = emptyMap()): TrackingV1Api {
        val apiClient = ApiClient()
        headers.forEach { (key, value) -> apiClient.addDefaultHeader(key, value) }
        apiClient.basePath = "${url}/rest"
        return TrackingV1Api(apiClient)
    }
}
