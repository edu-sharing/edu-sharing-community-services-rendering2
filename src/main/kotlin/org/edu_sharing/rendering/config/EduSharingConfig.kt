package org.edu_sharing.rendering.config

import org.edu_sharing.generated.repository.backend.services.rest.client.ApiClient
import org.edu_sharing.generated.repository.backend.services.rest.client.api.BulkV1Api
import org.edu_sharing.generated.repository.backend.services.rest.client.api.NodeV1Api
import org.edu_sharing.rendering.repository.rest.ApiClientFixes
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class EduSharingConfig {
    @Value("\${edu_sharing.basePath}")
    lateinit var restBasePath: String

    @Value("\${edu_sharing.username}")
    var username: String? = null

    @Value("\${edu_sharing.password}")
    var password: String? = null

    @Bean
    fun apiClient(): ApiClient {
        val apiClient: ApiClient = ApiClientFixes()
        apiClient.setBasePath("$restBasePath/rest")
        apiClient.setUsername(username)
        apiClient.setPassword(password)
        return apiClient
    }

    @Bean
    fun bulkV1Api(apiClient: ApiClient?): BulkV1Api {
        return BulkV1Api(apiClient)
    }

    @Bean
    fun nodeV1Api(apiClient: ApiClient?): NodeV1Api {
        return NodeV1Api(apiClient)
    }
}