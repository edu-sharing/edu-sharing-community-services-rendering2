package org.edu_sharing.rendering.config

import io.minio.MinioClient
import org.edu_sharing.generated.repository.backend.services.rest.client.ApiClient
import org.edu_sharing.generated.repository.backend.services.rest.client.api.BulkV1Api
import org.edu_sharing.generated.repository.backend.services.rest.client.api.NodeV1Api
import org.edu_sharing.rendering.repository.rest.ApiClientFixes
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import redis.clients.jedis.JedisPooled

@Configuration
class EduSharingConfig {
    @Value("\${edu_sharing.basePath}")
    var restBasePath: String? = null

    @Value("\${edu_sharing.username}")
    var username: String? = null

    @Value("\${edu_sharing.password}")
    var password: String? = null

    @Value("\${edu_sharing.minio_user}")
    var minioUser: String? = null

    @Value("\${edu_sharing.minio_password}")
    var minioPassword: String? = null

    @Value("\${edu_sharing.minio_url}")
    var minioUrl: String? = null

    @Value("\${edu_sharing.redis_url}")
    var redisUrl: String? = null

    @Value("\${edu_sharing.redis_port}")
    var redisPort: Int = 0

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

    @Bean
    fun eduMinioClient(): MinioClient {
        return MinioClient.builder().endpoint(minioUrl).credentials(minioUser, minioPassword).build()
    }

    @Bean
    fun eduRedisClient(): JedisPooled {
        return JedisPooled(redisUrl, redisPort)
    }
}