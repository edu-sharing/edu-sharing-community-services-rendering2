package org.edu_sharing.rendering.config

import io.lettuce.core.ClientOptions
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.DefaultValue
import java.time.Duration

/**
 * Lettuce client resilience settings (`app.redis.client.*`), shared by the standalone and the
 * cluster connection factory in [RedisConfig] so both behave identically when Redis is slow or
 * gone.
 *
 * @property commandTimeout Upper bound for one command (Lettuce' own default would be 60 s, far
 *   longer than any HTTP caller waits).
 * @property connectTimeout TCP connect timeout of a (re)connect attempt.
 * @property keepAlive TCP keep-alive; k8s conntrack/LBs silently reap idle flows, keep-alive makes
 *   a dead socket detectable.
 * @property requestQueueSize Max number of commands Lettuce buffers while the connection is down.
 *   Unbounded (the Lettuce default `Integer.MAX_VALUE`) it would grow in the heap during an outage,
 *   because every HTTP request touches Redis (sessions, node permissions). Excess commands fail
 *   immediately with a `RedisException`.
 * @property disconnectedBehavior What happens to commands issued while disconnected: `DEFAULT`
 *   (buffer them up to [requestQueueSize] while auto-reconnect is on, so short blips are absorbed),
 *   `ACCEPT_COMMANDS` (always buffer) or `REJECT_COMMANDS` (fail fast during any disconnect).
 */
@ConfigurationProperties(prefix = "app.redis.client")
data class RedisClientProperties(
    @DefaultValue("5s") val commandTimeout: Duration,
    @DefaultValue("10s") val connectTimeout: Duration,
    @DefaultValue("true") val keepAlive: Boolean,
    @DefaultValue("2000") val requestQueueSize: Int,
    @DefaultValue("DEFAULT") val disconnectedBehavior: ClientOptions.DisconnectedBehavior
)
