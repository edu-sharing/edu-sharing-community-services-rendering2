package org.edu_sharing.rendering.config

import io.micrometer.observation.Observation
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor
import io.micrometer.tracing.Tracer
import io.micrometer.tracing.propagation.Propagator
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

/**
 * Central [WebClient.Builder] bean.
 *
 * Spring Boot 4 does not auto-configure a reactive `WebClient.Builder` (nor a WebClient tracing
 * customizer) on this classpath, so we provide one and instrument it ourselves with a b3-propagating
 * [ExchangeFilterFunction] (matching Istio). The filter runs on a reactor thread where the trace
 * context is not available as a thread-local; instead it reads the active [Observation] captured into
 * the Reactor context by `spring.reactor.context-propagation=auto`, opens its scope to materialise the
 * trace context, and injects it via the configured [Propagator]. All WebClient configs/services inject
 * this builder and `clone()` it per use, so the instrumentation is inherited while each consumer keeps
 * its own base URL/codec settings.
 */
@Configuration
class WebClientConfig(
    private val tracer: Tracer,
    private val propagator: Propagator,
) {

    @Bean
    fun webClientBuilder(): WebClient.Builder =
        WebClient.builder().filter(b3PropagationFilter())

    private fun b3PropagationFilter(): ExchangeFilterFunction =
        ExchangeFilterFunction.ofRequestProcessor { request ->
            Mono.deferContextual { view ->
                val observation = view.getOrEmpty<Observation>(ObservationThreadLocalAccessor.KEY).orElse(null)
                val context = if (observation != null) {
                    observation.openScope().use { tracer.currentTraceContext().context() }
                } else {
                    tracer.currentTraceContext().context()
                }
                if (context == null) {
                    Mono.just(request)
                } else {
                    val builder = ClientRequest.from(request)
                    val setter = Propagator.Setter<ClientRequest.Builder> { carrier, key, value ->
                        carrier?.headers { headers -> headers.set(key, value) }
                    }
                    propagator.inject(context, builder, setter)
                    Mono.just(builder.build())
                }
            }
        }
}
