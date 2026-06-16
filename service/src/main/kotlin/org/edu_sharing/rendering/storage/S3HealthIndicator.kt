package org.edu_sharing.rendering.storage

import org.slf4j.LoggerFactory
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementContextAutoConfiguration
import org.springframework.boot.health.autoconfigure.contributor.ConditionalOnEnabledHealthIndicator
import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.s3.S3Client

@Component
@ConditionalOnClass(ManagementContextAutoConfiguration::class)
@ConditionalOnEnabledHealthIndicator("s3")
class S3HealthIndicator (
    private val s3Client: S3Client
): HealthIndicator {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun health(): Health {
        try {
            s3Client.listBuckets()
            log.debug("S3 health check: UP")
            return Health.up().withDetail("status", "S3 is available").build()
        } catch (e: Exception) {
            log.debug("S3 health check: DOWN — ${e.message}")
            return Health.down().withDetail("error", e.message ?: "unknown error").build()
        }
    }
}
