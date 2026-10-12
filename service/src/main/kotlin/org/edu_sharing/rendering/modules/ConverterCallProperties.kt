package org.edu_sharing.rendering.modules

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * Retry/timeout budget for calls of [ConverterWebServiceCaller] to the document and jupyter converters
 * (`app.converter.call.*`). Only transient failures (502/503/504, connection errors, timeouts) are retried.
 *
 * The defaults are staggered against the converters' own limits (document: 120 s execution, 300 s queue
 * timeout; jupyter: 300 s) and the reaper (30 min default max process time): one attempt may take up to
 * [attemptTimeout], and all attempts together never exceed [totalBudget], so a job cannot hang past the
 * reaper's threshold because of retries.
 */
@Component
@ConfigurationProperties("app.converter.call")
class ConverterCallProperties {
    /** Total number of attempts including the first one; 1 disables retrying. */
    var maxAttempts: Int = 3

    /** Backoff before the first retry; doubles per retry up to [maxBackoff] (with jitter). */
    var initialBackoff: Duration = Duration.ofSeconds(2)

    var maxBackoff: Duration = Duration.ofSeconds(20)

    /** Upper bound for a single attempt (request + streaming the result to disk). */
    var attemptTimeout: Duration = Duration.ofSeconds(330)

    /** Upper bound for all attempts and the pauses between them. */
    var totalBudget: Duration = Duration.ofMinutes(15)

    /** Cap for the delay requested by a 503 `Retry-After` header. */
    var maxRetryAfter: Duration = Duration.ofSeconds(30)
}
