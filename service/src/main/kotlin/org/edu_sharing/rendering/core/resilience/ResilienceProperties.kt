package org.edu_sharing.rendering.core.resilience

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * Resilience settings for calls to external systems (Sodix, Omega, DDB, GitHub, lumi), bound from
 * `app.resilience.*`. The top-level sections are the defaults for every guarded remote; [modules] overrides
 * single values per remote name (`sodix`, `omega`, `omega-validation`, `ddb`, `github`, `lumi`).
 *
 * All values have code defaults, so nothing has to be configured. `app.resilience.enabled=false` turns the
 * whole layer (retry, circuit breaker, bulkhead) into a pass-through.
 */
@Component
@ConfigurationProperties("app.resilience")
class ResilienceProperties {
    var enabled: Boolean = true
    var retry: RetryProperties = RetryProperties()
    var circuitBreaker: CircuitBreakerProperties = CircuitBreakerProperties()
    var bulkhead: BulkheadProperties = BulkheadProperties()

    /** Per-remote overrides keyed by the guard name; unset fields fall back to the sections above. */
    var modules: Map<String, ModuleOverride> = emptyMap()

    class RetryProperties {
        /** Total attempts including the first one; 1 disables retries. Only idempotent GET/HEAD calls are retried. */
        var maxAttempts: Int = 3
        var initialBackoff: Duration = Duration.ofMillis(200)
        var maxBackoff: Duration = Duration.ofSeconds(2)

        /** Fraction (0..1) of each backoff that is randomised downwards, to avoid synchronised retry storms. */
        var jitter: Double = 0.5

        /** A 429 whose `Retry-After` exceeds this is not retried (the job fails instead of waiting). */
        var maxRetryAfter: Duration = Duration.ofSeconds(5)

        /**
         * Upper bound for one guarded call including all attempts and backoffs. Must stay below the queue/reaper
         * timeouts so a retrying call can never keep a job longer than a single hanging call would.
         */
        var totalBudget: Duration = Duration.ofSeconds(75)
    }

    class CircuitBreakerProperties {
        var enabled: Boolean = true

        /** Consecutive infrastructure failures (5xx, connect/reset, timeout) that open the breaker. */
        var failureThreshold: Int = 5

        /** How long the breaker rejects calls before it lets probe calls through (HALF_OPEN). */
        var openDuration: Duration = Duration.ofSeconds(30)

        /** Concurrent probe calls in HALF_OPEN; one success closes the breaker, one failure re-opens it. */
        var halfOpenMaxCalls: Int = 1
    }

    class BulkheadProperties {
        var enabled: Boolean = true

        /** Concurrent in-flight calls per remote and pod; further calls fail fast. Keep >= the queue's concurrency. */
        var maxConcurrentCalls: Int = 64

        /** How long a call waits for a free slot (blocks the calling thread); zero rejects immediately. */
        var maxWait: Duration = Duration.ZERO
    }

    class ModuleOverride {
        var maxAttempts: Int? = null
        var failureThreshold: Int? = null
        var openDuration: Duration? = null
        var maxConcurrentCalls: Int? = null
    }

    /** Effective, immutable settings of one remote. */
    fun settingsFor(name: String): RemoteCallSettings {
        val override = modules[name]
        return RemoteCallSettings(
            enabled = enabled,
            maxAttempts = (override?.maxAttempts ?: retry.maxAttempts).coerceAtLeast(1),
            initialBackoff = retry.initialBackoff,
            maxBackoff = retry.maxBackoff,
            jitter = retry.jitter.coerceIn(0.0, 1.0),
            maxRetryAfter = retry.maxRetryAfter,
            totalBudget = retry.totalBudget,
            breakerEnabled = circuitBreaker.enabled,
            failureThreshold = (override?.failureThreshold ?: circuitBreaker.failureThreshold).coerceAtLeast(1),
            openDuration = override?.openDuration ?: circuitBreaker.openDuration,
            halfOpenMaxCalls = circuitBreaker.halfOpenMaxCalls.coerceAtLeast(1),
            bulkheadEnabled = bulkhead.enabled,
            maxConcurrentCalls = (override?.maxConcurrentCalls ?: bulkhead.maxConcurrentCalls).coerceAtLeast(1),
            bulkheadMaxWait = bulkhead.maxWait,
        )
    }
}

/** Resolved settings of one remote (see [ResilienceProperties.settingsFor]). */
data class RemoteCallSettings(
    val enabled: Boolean = true,
    val maxAttempts: Int = 3,
    val initialBackoff: Duration = Duration.ofMillis(200),
    val maxBackoff: Duration = Duration.ofSeconds(2),
    val jitter: Double = 0.5,
    val maxRetryAfter: Duration = Duration.ofSeconds(5),
    val totalBudget: Duration = Duration.ofSeconds(75),
    val breakerEnabled: Boolean = true,
    val failureThreshold: Int = 5,
    val openDuration: Duration = Duration.ofSeconds(30),
    val halfOpenMaxCalls: Int = 1,
    val bulkheadEnabled: Boolean = true,
    val maxConcurrentCalls: Int = 64,
    val bulkheadMaxWait: Duration = Duration.ZERO,
)
