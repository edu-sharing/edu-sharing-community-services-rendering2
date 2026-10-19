package org.edu_sharing.rendering.core.resilience

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import java.time.Duration

/** Registry for module tests: real retry/breaker behaviour, but with millisecond backoffs. */
fun testGuardRegistry(
    meterRegistry: SimpleMeterRegistry = SimpleMeterRegistry(),
    configure: ResilienceProperties.() -> Unit = {},
): RemoteCallGuardRegistry {
    val properties = ResilienceProperties().apply {
        retry.initialBackoff = Duration.ofMillis(5)
        retry.maxBackoff = Duration.ofMillis(20)
        configure()
    }
    return RemoteCallGuardRegistry(properties, meterRegistry)
}
