package org.edu_sharing.rendering.storage

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.DefaultValue
import java.time.Duration

/**
 * Settings of [S3HealthIndicator] (`app.s3.health.*`).
 *
 * @property bucket Bucket the probe sends a `HEAD` request to. It does not have to exist: a `404`
 *   answer already proves that the endpoint is reachable and the credentials are accepted.
 * @property timeout Deadline of the probe, so a hanging endpoint cannot block the health thread.
 */
@ConfigurationProperties(prefix = "app.s3.health")
data class S3HealthProperties(
    @DefaultValue("rendering-health-probe") val bucket: String,
    @DefaultValue("5s") val timeout: Duration
)
