package org.edu_sharing.rendering.core.resilience

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * Resilience settings for calls to external systems (Sodix, Omega, DDB, GitHub, lumi), bound from
 * `app.resilience.*`. The top-level sections are the defaults for every guarded remote; [modules] overrides
 * single values per remote name (`sodix`, `omega`, `omega-validation`, `ddb`, `github`, `lumi`, `lumi-proxy`, `lumi-upload`).
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

    companion object {
        /** Guard name of the lumi reverse proxy (user-facing, high fan-out per page). */
        const val LUMI_PROXY_GUARD = "lumi-proxy"

        /** Guard name of the lumi package upload (a side-effecting POST, never retried). */
        const val LUMI_UPLOAD_GUARD = "lumi-upload"

        /**
         * Code defaults of remotes whose profile differs from the generic sections: the lumi proxy loads many files
         * in parallel per H5P page (high bulkhead), recovers quickly (short open duration limits the mixed phase of
         * pod-local breakers) and only counts reachability failures. Overridable via `modules.lumi-proxy.*`.
         */
        internal val BUILT_IN_MODULE_DEFAULTS: Map<String, ModuleOverride> = mapOf(
            LUMI_PROXY_GUARD to ModuleOverride().apply {
                maxConcurrentCalls = 256
                openDuration = Duration.ofSeconds(10)
                failureClassification = FailureClassification.REACHABILITY_ONLY
                // The header timeout (app.lumi.proxy.response-headers-timeout) is the real bound; this is a backstop.
                totalBudget = Duration.ofMinutes(5)
            },
            // The upload runs for minutes (repository credential `timeout`, default 300 s, capped at 600 s by the
            // WebClient), so the generic 75 s budget would cut it. Never retried (POST), default classification.
            LUMI_UPLOAD_GUARD to ModuleOverride().apply {
                maxAttempts = 1
                totalBudget = Duration.ofMinutes(15)
            },
        )
    }

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

        /** Which failures count against the breaker; see [FailureClassification]. */
        var failureClassification: FailureClassification = FailureClassification.DEFAULT
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
        var failureClassification: FailureClassification? = null

        /** Upper bound of one call incl. retries (see [RetryProperties.totalBudget]). */
        var totalBudget: Duration? = null
    }

    /**
     * Effective, immutable settings of one remote. Precedence per value: configured `modules.<name>`, then the
     * built-in defaults of that remote ([BUILT_IN_MODULE_DEFAULTS], `lumi-proxy` and `lumi-upload`), then the sections above.
     */
    fun settingsFor(name: String): RemoteCallSettings {
        val configured = modules[name]
        val builtIn = BUILT_IN_MODULE_DEFAULTS[name]
        return RemoteCallSettings(
            enabled = enabled,
            maxAttempts = (configured?.maxAttempts ?: builtIn?.maxAttempts ?: retry.maxAttempts).coerceAtLeast(1),
            initialBackoff = retry.initialBackoff,
            maxBackoff = retry.maxBackoff,
            jitter = retry.jitter.coerceIn(0.0, 1.0),
            maxRetryAfter = retry.maxRetryAfter,
            totalBudget = configured?.totalBudget ?: builtIn?.totalBudget ?: retry.totalBudget,
            breakerEnabled = circuitBreaker.enabled,
            failureThreshold = (configured?.failureThreshold ?: builtIn?.failureThreshold ?: circuitBreaker.failureThreshold).coerceAtLeast(1),
            openDuration = configured?.openDuration ?: builtIn?.openDuration ?: circuitBreaker.openDuration,
            halfOpenMaxCalls = circuitBreaker.halfOpenMaxCalls.coerceAtLeast(1),
            failureClassification = configured?.failureClassification ?: builtIn?.failureClassification
                ?: circuitBreaker.failureClassification,
            bulkheadEnabled = bulkhead.enabled,
            maxConcurrentCalls = (configured?.maxConcurrentCalls ?: builtIn?.maxConcurrentCalls ?: bulkhead.maxConcurrentCalls).coerceAtLeast(1),
            bulkheadMaxWait = bulkhead.maxWait,
        )
    }
}

/** Which failures count against a circuit breaker. */
enum class FailureClassification {
    /** 5xx (not 501), connect/reset, timeouts, pool wait timeout; a 4xx proves the remote is alive. */
    DEFAULT,

    /**
     * Only reachability problems: connect/reset/timeout failures and HTTP 502/503/504. A 500/507 (e.g. one
     * broken piece of content that users keep reloading) does not count and resets the failure streak.
     */
    REACHABILITY_ONLY,
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
    val failureClassification: FailureClassification = FailureClassification.DEFAULT,
    val bulkheadEnabled: Boolean = true,
    val maxConcurrentCalls: Int = 64,
    val bulkheadMaxWait: Duration = Duration.ZERO,
)
