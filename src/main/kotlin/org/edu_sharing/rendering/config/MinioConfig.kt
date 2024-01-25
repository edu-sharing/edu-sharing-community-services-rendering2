package org.edu_sharing.rendering.config

import io.minio.MinioClient
import org.edu_sharing.rendering.blobStorage.MinioService
import org.edu_sharing.rendering.blobStorage.StorageService
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class MinioConfig {

    @Value("\${edu_sharing.minio_user}")
    var minioUser: String? = null

    @Value("\${edu_sharing.minio_password}")
    var minioPassword: String? = null

    @Value("\${edu_sharing.minio_url}")
    var minioUrl: String? = null

    @Bean
    fun eduMinioClient(): MinioClient {
        return MinioClient.builder().endpoint(minioUrl).credentials(minioUser, minioPassword).build()
    }

    @Bean
    fun storageImplementation(): StorageService {
        return MinioService(eduMinioClient())
    }
}