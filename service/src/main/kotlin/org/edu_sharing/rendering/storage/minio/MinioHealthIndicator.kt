package org.edu_sharing.rendering.storage.minio

import io.minio.BucketExistsArgs
import io.minio.MinioClient
import org.springframework.boot.actuate.autoconfigure.health.ConditionalOnEnabledHealthIndicator
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementContextAutoConfiguration
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.stereotype.Component

@Component
@ConditionalOnClass(ManagementContextAutoConfiguration::class)
@ConditionalOnEnabledHealthIndicator("minio")
class MinioHealthIndicator(
    private val minioClient: MinioClient
) : HealthIndicator {

    override fun health(): Health {
        try {
            // Ping MinIO by checking the bucket
            minioClient.bucketExists(BucketExistsArgs.builder().bucket("foo").build())
            return Health.up().withDetail("status", "MinIO is available").build()
        } catch (e: Exception) {
            return Health.down()
                .withDetail("error", e.message)
                .build()
        }
    }
}
