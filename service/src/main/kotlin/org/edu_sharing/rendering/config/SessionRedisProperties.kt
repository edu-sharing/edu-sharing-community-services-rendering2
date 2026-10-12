package org.edu_sharing.rendering.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.DefaultValue

/**
 * Spring Session's Redis bootstrap (`app.redis.session.*`).
 *
 * @property configureAction `DEFAULT` lets Spring Session run `CONFIG SET notify-keyspace-events Egx`
 *   once at startup (needed for the indexed repository's expiry events). Managed Redis offerings
 *   (ElastiCache, Azure Cache, ...) usually forbid `CONFIG`; there set `NO_OP` and configure
 *   `notify-keyspace-events Egx` on the server instead. Note: with `DEFAULT` the startup needs a
 *   reachable Redis (fail-fast, the pod restarts until it is up).
 */
@ConfigurationProperties(prefix = "app.redis.session")
data class SessionRedisProperties(
    @DefaultValue("DEFAULT") val configureAction: ConfigureAction
) {
    enum class ConfigureAction { DEFAULT, NO_OP }
}
