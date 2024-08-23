package org.edu_sharing.rendering.config

import io.minio.MinioClient
import io.minio.admin.MinioAdminClient
import org.edu_sharing.rendering.blobStorage.BucketStrategy
import org.edu_sharing.rendering.blobStorage.MinioService
import org.edu_sharing.rendering.blobStorage.StorageService
import org.edu_sharing.rendering.repository.mongo.TrackingEntryRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class MinioConfig {

    @Value("\${app.minio.user}")
    var minioUser: String? = null

    @Value("\${app.minio.password}")
    var minioPassword: String? = null

    @Value("\${app.minio.url}")
    var minioUrl: String? = null

    @Bean
    fun eduMinioClient(): MinioClient {
        return MinioClient.builder().endpoint(minioUrl).credentials(minioUser, minioPassword).build()
    }

    @Bean
    fun eduMinioAdminClient(): MinioAdminClient {
        return MinioAdminClient.builder().endpoint(minioUrl).credentials(minioUser, minioPassword).build()
    }
}
