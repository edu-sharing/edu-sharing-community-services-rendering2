package org.edu_sharing.rendering.integration

import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationRegistry
import io.micrometer.tracing.Tracer
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.reactive.function.client.WebClient

/**
 * Verifies that the tracing infrastructure produces b3-conformant ids and that the central
 * [WebClient.Builder] propagates the active trace context to outbound calls as a b3 header (as happens
 * in production, where a server-side observation is always in scope when a controller calls WebClient).
 *
 * This is the behaviour we keep so the Istio sidecar can stitch the trace together; the app itself
 * exports no spans. b3 propagation and full sampling are configured in the shared test
 * `application.properties`; the span processor that records spans (the prerequisite for header
 * injection) is always active regardless of any export setting.
 */
class TracePropagationIntegrationTest(
    @param:Autowired private val tracer: Tracer,
    @param:Autowired private val observationRegistry: ObservationRegistry,
    @param:Autowired private val webClientBuilder: WebClient.Builder,
) : AbstractIntegrationTest() {

    private lateinit var mockServer: MockWebServer

    @BeforeEach
    fun setup() {
        mockServer = MockWebServer()
        mockServer.start()
    }

    @AfterEach
    fun tearDown() {
        mockServer.shutdown()
    }

    @Test
    fun generatedTraceIdIsB3ConformantHex() {
        val span = tracer.nextSpan().name("test").start()
        try {
            val traceId = span.context().traceId()
            // b3 / Zipkin trace ids are 16 or 32 lowercase hex chars (here: 128-bit).
            assert(traceId.matches(Regex("[0-9a-f]{32}"))) { "Unexpected trace id format: $traceId" }
        } finally {
            span.end()
        }
    }

    @Test
    fun webClientPropagatesActiveTraceContextAsB3Header() {
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("ok"))
        val client = webClientBuilder.clone().baseUrl(mockServer.url("/").toString()).build()

        // Run the outbound call inside an observation scope, mirroring an inbound-request-driven call.
        val traceIdHolder = arrayOfNulls<String>(1)
        Observation.createNotStarted("test", observationRegistry).observe(Runnable {
            traceIdHolder[0] = tracer.currentSpan()?.context()?.traceId()
            client.get().uri("/ping").retrieve().toEntity(String::class.java).block()
        })

        assert(traceIdHolder[0] != null) { "Expected an active span (trace id) inside the observation scope" }

        val recorded = mockServer.takeRequest()
        val b3 = recorded.getHeader("b3")
        assert(b3 != null) { "Expected a b3 header on the outbound request; headers were: ${recorded.headers}" }
        assert(b3!!.startsWith(traceIdHolder[0]!!)) {
            "b3 header '$b3' should carry the active trace id ${traceIdHolder[0]}"
        }
    }
}
