package org.edu_sharing.rendering.core.resilience

import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * Hands out one [RemoteCallGuard] per remote name (`sodix`, `omega`, `omega-validation`, `ddb`, `github`, `lumi`),
 * so the circuit breaker and bulkhead state is shared by every caller of that remote on the pod. Settings come
 * from [ResilienceProperties]; metrics are registered once per guard (`rendering.remote.*`).
 */
@Component
class RemoteCallGuardRegistry(
    private val properties: ResilienceProperties,
    private val meterRegistry: MeterRegistry,
) {
    private val guards = ConcurrentHashMap<String, RemoteCallGuard>()

    fun guard(name: String): RemoteCallGuard =
        guards.computeIfAbsent(name) { RemoteCallGuard(it, properties.settingsFor(it), meterRegistry) }
}
