package org.edu_sharing.rendering.core.resilience

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import java.net.ConnectException
import java.net.URI
import java.time.Duration
import java.util.concurrent.TimeoutException

class FailureClassificationTest {
    private fun http(status: Int) =
        WebClientResponseException.create(status, "x", HttpHeaders(), ByteArray(0), null)

    private fun guard(classification: FailureClassification) = RemoteCallGuard(
        "m", RemoteCallSettings(maxAttempts = 1, failureThreshold = 2, failureClassification = classification)
    )

    private fun fail(guard: RemoteCallGuard, failure: Throwable) {
        runCatching { guard.block(false) { Mono.error<String>(failure) } }
    }

    private fun isOpen(guard: RemoteCallGuard) = guard.breaker!!.state() == CircuitBreaker.State.OPEN

    @Test
    fun `reachability classification counts 502, 503, 504, connect errors and timeouts only`() {
        listOf(502, 503, 504).forEach { assertTrue(RemoteCallGuard.isReachabilityFailure(http(it)), "$it") }
        listOf(400, 404, 500, 501, 507).forEach { assertFalse(RemoteCallGuard.isReachabilityFailure(http(it)), "$it") }
        val connect = WebClientRequestException(
            ConnectException("refused"), org.springframework.http.HttpMethod.GET, URI("http://h/"), HttpHeaders()
        )
        assertTrue(RemoteCallGuard.isReachabilityFailure(connect))
        assertTrue(RemoteCallGuard.isReachabilityFailure(TimeoutException("slow")))
    }

    @Test
    fun `default classification still counts every 5xx except 501`() {
        listOf(500, 502, 503, 504, 507).forEach { assertTrue(RemoteCallGuard.isInfrastructureFailure(http(it)), "$it") }
        assertFalse(RemoteCallGuard.isInfrastructureFailure(http(501)))
    }

    @Test
    fun `reachability guard stays closed on repeated 500 and 507`() {
        val guard = guard(FailureClassification.REACHABILITY_ONLY)
        repeat(5) { fail(guard, http(500)); fail(guard, http(507)) }
        assertFalse(isOpen(guard))
    }

    @Test
    fun `a 500 in between resets the failure streak of a reachability guard`() {
        val guard = guard(FailureClassification.REACHABILITY_ONLY)
        fail(guard, http(503))
        fail(guard, http(500))
        fail(guard, http(503))
        assertFalse(isOpen(guard))
    }

    @Test
    fun `reachability guard opens on consecutive 503 and on timeouts`() {
        val g503 = guard(FailureClassification.REACHABILITY_ONLY)
        repeat(2) { fail(g503, http(503)) }
        assertTrue(isOpen(g503))
        val gTimeout = guard(FailureClassification.REACHABILITY_ONLY)
        repeat(2) { fail(gTimeout, TimeoutException("slow")) }
        assertTrue(isOpen(gTimeout))
    }

    @Test
    fun `default guard opens on consecutive 500`() {
        val guard = guard(FailureClassification.DEFAULT)
        repeat(2) { fail(guard, http(500)) }
        assertTrue(isOpen(guard))
    }

    @Test
    fun `lumi proxy and upload guards have their own defaults, other guards are unchanged`() {
        val props = ResilienceProperties()
        val proxy = props.settingsFor("lumi-proxy")
        assertEquals(FailureClassification.REACHABILITY_ONLY, proxy.failureClassification)
        assertEquals(256, proxy.maxConcurrentCalls)
        assertEquals(Duration.ofSeconds(10), proxy.openDuration)
        assertEquals(Duration.ofMinutes(5), proxy.totalBudget)

        val upload = props.settingsFor("lumi-upload")
        assertEquals(FailureClassification.DEFAULT, upload.failureClassification)
        assertEquals(1, upload.maxAttempts)
        assertEquals(Duration.ofMinutes(15), upload.totalBudget)
        assertEquals(64, upload.maxConcurrentCalls)
        assertEquals(Duration.ofSeconds(30), upload.openDuration)

        val lumi = props.settingsFor("lumi")
        assertEquals(RemoteCallSettings(), lumi)
        assertEquals(RemoteCallSettings(), props.settingsFor("sodix"))
    }

    @Test
    fun `configured module values override built-in and global values`() {
        val props = ResilienceProperties().apply {
            circuitBreaker.failureClassification = FailureClassification.REACHABILITY_ONLY
            modules = mapOf(
                "lumi-proxy" to ResilienceProperties.ModuleOverride().apply {
                    maxConcurrentCalls = 10
                    failureClassification = FailureClassification.DEFAULT
                }
            )
        }
        val proxy = props.settingsFor("lumi-proxy")
        assertEquals(10, proxy.maxConcurrentCalls)
        assertEquals(FailureClassification.DEFAULT, proxy.failureClassification)
        assertEquals(Duration.ofSeconds(10), proxy.openDuration)
        // the global section applies to guards without an override
        assertEquals(FailureClassification.REACHABILITY_ONLY, props.settingsFor("ddb").failureClassification)
    }
}
