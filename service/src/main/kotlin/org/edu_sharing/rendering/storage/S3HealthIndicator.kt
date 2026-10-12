package org.edu_sharing.rendering.storage

import org.slf4j.LoggerFactory
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementContextAutoConfiguration
import org.springframework.boot.health.autoconfigure.contributor.ConditionalOnEnabledHealthIndicator
import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.NoSuchBucketException
import software.amazon.awssdk.services.s3.model.S3Exception

/**
 * Probes S3 with a single `HEAD bucket` (cheap, needs no `ListAllMyBuckets` permission, unlike
 * `listBuckets`) under a hard deadline. A `404` for the probe bucket counts as UP: the endpoint
 * answered and accepted the credentials.
 */
@Component
@ConditionalOnClass(ManagementContextAutoConfiguration::class)
@ConditionalOnEnabledHealthIndicator("s3")
class S3HealthIndicator (
    private val s3Client: S3Client,
    private val properties: S3HealthProperties
): HealthIndicator {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun health(): Health {
        try {
            try {
                s3Client.headBucket { request ->
                    request.bucket(properties.bucket)
                        .overrideConfiguration { override ->
                            override.apiCallTimeout(properties.timeout)
                            override.apiCallAttemptTimeout(properties.timeout)
                        }
                }
            } catch (_: NoSuchBucketException) {
                // endpoint reachable, the probe bucket just does not exist
            } catch (e: S3Exception) {
                if (e.statusCode() != 404) throw e
            }
            log.debug("S3 health check: UP")
            return Health.up().withDetail("status", "S3 is available").build()
        } catch (e: Exception) {
            log.debug("S3 health check: DOWN — ${e.message}")
            return Health.down().withDetail("error", e.message ?: "unknown error").build()
        }
    }
}
