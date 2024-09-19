package org.edu_sharing.rendering.edusharingRepo.config

import org.edu_sharing.generated.repository.backend.services.rest.client.ApiClient
import org.edu_sharing.generated.repository.backend.services.rest.client.api.AdminV1Api
import org.edu_sharing.generated.repository.backend.services.rest.client.api.BulkV1Api
import org.edu_sharing.generated.repository.backend.services.rest.client.api.NodeV1Api
import org.edu_sharing.rendering.edusharingRepo.api.ApiClientFixes
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class EduSharingConfig {
    @Value("\${edu_sharing.basePath}")
    lateinit var eduBasePath: String

    @Value("\${edu_sharing.username}")
    lateinit var username: String

    @Value("\${edu_sharing.password}")
    lateinit var password: String

    @Bean
    fun apiClient(): ApiClient {
        val apiClient: ApiClient = ApiClientFixes()
        apiClient.setBasePath("$eduBasePath/rest")
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

    @Bean
    fun adminApi(apiClient: ApiClient?): AdminV1Api {
        return AdminV1Api(apiClient)
    }

    @Bean
    fun eduSharingWebClient(): WebClient {
        return WebClient
            .builder()
            .baseUrl(eduBasePath)
            .build()
    }
}
