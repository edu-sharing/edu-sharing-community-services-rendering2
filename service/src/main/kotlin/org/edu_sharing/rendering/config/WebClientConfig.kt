package org.edu_sharing.rendering.config

import io.micrometer.observation.Observation
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor
import io.micrometer.tracing.Tracer
import io.micrometer.tracing.propagation.Propagator
import io.netty.channel.ChannelOption
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.util.unit.DataSize
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import reactor.netty.http.Http11SslContextSpec
import reactor.netty.http.client.HttpClient
import reactor.netty.tcp.SslProvider
import java.time.Duration

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
    @param:Value($$"${app.webclient.connect-timeout-millis:10000}")
    private val connectTimeoutMillis: Int,
    @param:Value($$"${app.webclient.response-timeout-seconds:60}")
    private val responseTimeoutSeconds: Long,
    @param:Value($$"${app.webclient.long-running-response-timeout-seconds:600}")
    private val longRunningResponseTimeoutSeconds: Long,
    @param:Value($$"${spring.http.codecs.max-in-memory-size:256KB}")
    private val maxInMemorySize: DataSize,
) {

    /**
     * Default builder with sane connect/response timeouts. Without it a hanging downstream service
     * would block the calling worker indefinitely on the many `.block()` call sites. Used for quick
     * API calls (sodix, ddb, github metadata, repo registration, …). All consumers `clone()` this
     * builder, so the connector + tracing instrumentation is inherited.
     *
     * Marked [Primary] so the many `WebClient.Builder` injections without a qualifier keep resolving
     * to this one; long-running consumers must opt in via the qualified bean below.
     */
    @Bean
    @Primary
    fun webClientBuilder(): WebClient.Builder = builderWithResponseTimeout(responseTimeoutSeconds)

    /**
     * Builder for inherently slow calls — file conversions ([ConverterWebServiceCaller] clients) and
     * content uploads (lumi/onyx/moodle). These can legitimately run far longer than the default
     * response timeout, so they get a separate, larger budget configurable via
     * `app.webclient.long-running-response-timeout-seconds`.
     */
    @Bean("longRunningWebClientBuilder")
    fun longRunningWebClientBuilder(): WebClient.Builder =
        builderWithResponseTimeout(longRunningResponseTimeoutSeconds)

    /**
     * Builder that suppresses the TLS SNI extension (empty `serverNames`) on outbound HTTPS.
     *
     * Some upstreams (notably `cp.sodis.de`, an Apache/mod_ssl host with a `*.sodis.de` wildcard cert)
     * answer a ClientHello that carries an SNI `server_name` with a **warning-level**
     * `unrecognized_name(112)` alert. OpenSSL/curl ignore warning alerts, but the JDK TLS provider —
     * which Reactor Netty falls back to here because no `netty-tcnative`/BoringSSL is on the classpath —
     * escalates it to a fatal `handshake_failure` ("received handshake warning: unrecognized_name").
     * Not sending SNI stops the server emitting the alert; hostname verification still succeeds because
     * the wildcard cert is served regardless of SNI. Use this builder ONLY for such SNI-hostile hosts —
     * hosts that require SNI (shared IP / vhost) must keep the default builders above.
     */
    @Bean("noSniWebClientBuilder")
    fun noSniWebClientBuilder(): WebClient.Builder =
        builderWithResponseTimeout(responseTimeoutSeconds, disableSni = true)

    private fun builderWithResponseTimeout(
        responseTimeoutSeconds: Long,
        disableSni: Boolean = false,
    ): WebClient.Builder {
        var httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMillis)
            .responseTimeout(Duration.ofSeconds(responseTimeoutSeconds))
        if (disableSni) {
            // Typed as GenericSslContextSpec so overload resolution avoids the deprecated
            // sslContext(ProtocolSslContextSpec) overload that Http11SslContextSpec would otherwise bind to.
            val sslContextSpec: SslProvider.GenericSslContextSpec<*> = Http11SslContextSpec.forClient()
            httpClient = httpClient.secure { spec ->
                spec.sslContext(sslContextSpec)
                    // Netty pre-populates SNI from the peer host; clear it on the engine before the
                    // handshake so no server_name extension is sent.
                    .handlerConfigurator { sslHandler ->
                        val engine = sslHandler.engine()
                        val params = engine.sslParameters
                        params.serverNames = emptyList()
                        engine.sslParameters = params
                    }
            }
        }
        return WebClient.builder()
            .clientConnector(ReactorClientHttpConnector(httpClient))
            // Apply the buffer limit centrally (Spring Boot 4 does not auto-configure the builder, so
            // `spring.http.codecs.max-in-memory-size` would otherwise only apply where set by hand and
            // every other client would stay on the 256 KB default). Consumers inherit it via clone().
            .codecs { it.defaultCodecs().maxInMemorySize(maxInMemorySize.toBytes().toInt()) }
            .filter(b3PropagationFilter())
    }

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
