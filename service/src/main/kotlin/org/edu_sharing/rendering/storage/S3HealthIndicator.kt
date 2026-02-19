package org.edu_sharing.rendering.storage

import org.springframework.boot.actuate.autoconfigure.health.ConditionalOnEnabledHealthIndicator
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementContextAutoConfiguration
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.s3.S3Client

@Component
@ConditionalOnClass(ManagementContextAutoConfiguration::class)
@ConditionalOnEnabledHealthIndicator("s3")
class S3HealthIndicator (
    private val s3Client: S3Client
): HealthIndicator {
    override fun health(): Health {
        try {
            s3Client.listBuckets()
            return Health.up().withDetail("status", "S3 is available").build()
        } catch (e: Exception) {
            return Health.down().withDetail("error", e.message).build()
        }
    }
}
