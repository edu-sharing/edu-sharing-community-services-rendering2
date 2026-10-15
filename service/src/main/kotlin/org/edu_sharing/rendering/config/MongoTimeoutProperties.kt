package org.edu_sharing.rendering.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.DefaultValue
import java.time.Duration

/**
 * Explicit MongoDB driver timeouts (`app.mongodb.timeout.*`). Boot 4's `spring.mongodb.*` has no
 * timeout keys, and the driver defaults leave `socketTimeout` infinite, so a half-open connection
 * (network partition, silently dropped conntrack entry) would block a request or consumer thread
 * forever. Applied through a `MongoClientSettingsBuilderCustomizer` in [MongoConfig].
 *
 * The defaults are conservative on purpose: they only bound *stuck* calls, never legitimately slow
 * ones (the Tracking aggregations of the cache cleaner can take a while on large collections).
 *
 * @property connectTimeout TCP connect timeout per connection.
 * @property socketTimeout Max wait for a single read on a socket (i.e. for one command reply).
 *   `Duration.ZERO` = infinite (driver default).
 * @property serverSelectionTimeout Max wait for a suitable server (e.g. during a primary election)
 *   before an operation fails. This is also what a request waits when Mongo is down.
 */
@ConfigurationProperties(prefix = "app.mongodb.timeout")
data class MongoTimeoutProperties(
    @DefaultValue("10s") val connectTimeout: Duration,
    @DefaultValue("5m") val socketTimeout: Duration,
    @DefaultValue("30s") val serverSelectionTimeout: Duration
)
