package org.edu_sharing.rendering.core.resilience

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpHeaders
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import reactor.netty.internal.shaded.reactor.pool.PoolAcquirePendingLimitException
import reactor.netty.internal.shaded.reactor.pool.PoolAcquireTimeoutException
import java.io.IOException
import java.net.ConnectException
import java.net.URI
import java.time.Duration
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

class RemoteCallGuardTest {
    private val meters = SimpleMeterRegistry()

    private fun http(status: Int, retryAfter: String? = null) = WebClientResponseException.create(
        status, "x",
        HttpHeaders().also { h -> retryAfter?.let { h.set("Retry-After", it) } }, ByteArray(0), null
    )

    private fun request(cause: Throwable) =
        WebClientRequestException(cause, org.springframework.http.HttpMethod.GET, URI("http://h/"), HttpHeaders())

    private fun guard(
        settings: RemoteCallSettings = RemoteCallSettings(
            initialBackoff = Duration.ofMillis(100), maxBackoff = Duration.ofMillis(400), jitter = 0.5,
            maxAttempts = 3, failureThreshold = 3,
        ),
        random: () -> Double = { 0.0 },
    ) = RemoteCallGuard("m", settings.copy(initialBackoff = settings.initialBackoff), meters, random = random)

    /** Guard with ms-scale backoffs so retries can run in real time. */
    private fun fastGuard(maxAttempts: Int = 3, threshold: Int = 3, maxConcurrent: Int = 64) = RemoteCallGuard(
        "m",
        RemoteCallSettings(
            maxAttempts = maxAttempts, initialBackoff = Duration.ofMillis(2), maxBackoff = Duration.ofMillis(8),
            failureThreshold = threshold, maxConcurrentCalls = maxConcurrent
        ),
        meters
    )

    @Test
    fun `backoff is exponential, capped and jittered downwards only`() {
        val noJitter = guard(random = { 0.0 })
        assertEquals(Duration.ofMillis(100), noJitter.retryDelay(0, http(503)))
        assertEquals(Duration.ofMillis(200), noJitter.retryDelay(1, http(503)))
        assertEquals(Duration.ofMillis(400), noJitter.retryDelay(2, http(503)))
        assertEquals(Duration.ofMillis(400), noJitter.retryDelay(10, http(503)))

        val maxJitter = guard(random = { 1.0 })
        assertEquals(Duration.ofMillis(50), maxJitter.retryDelay(0, http(503)))
        assertEquals(Duration.ofMillis(200), maxJitter.retryDelay(20, http(503)))
    }

    @Test
    fun `retry-after raises the delay but is capped by maxRetryAfter`() {
        val guard = guard(random = { 0.0 })
        assertEquals(Duration.ofSeconds(3), guard.retryDelay(0, http(429, "3")))
        assertNull(guard.retryDelay(0, http(429, "30")))
        assertNull(guard.retryDelay(0, http(429)))
    }

    @Test
    fun `only transient failures are retriable`() {
        val guard = guard()
        assertTrue(guard.retryDelay(0, http(500)) != null)
        assertTrue(guard.retryDelay(0, http(503)) != null)
        assertTrue(guard.retryDelay(0, request(ConnectException("refused"))) != null)
        assertTrue(guard.retryDelay(0, request(IOException("Connection reset by peer"))) != null)
        assertNull(guard.retryDelay(0, http(501)))
        assertNull(guard.retryDelay(0, http(400)))
        assertNull(guard.retryDelay(0, http(404)))
        assertNull(guard.retryDelay(0, TimeoutException()))
        assertNull(guard.retryDelay(0, request(io.netty.handler.timeout.ReadTimeoutException.INSTANCE)))
        assertNull(guard.retryDelay(0, request(javax.net.ssl.SSLHandshakeException("tls"))))
        assertNull(guard.retryDelay(0, IllegalStateException("decoding")))
    }

    @Test
    fun `retries a transient failure and then succeeds`() {
        val attempts = AtomicInteger()
        val result = fastGuard().block(retryable = true) {
            Mono.defer { if (attempts.incrementAndGet() < 3) Mono.error(http(503)) else Mono.just("ok") }
        }

        assertEquals("ok", result)
        assertEquals(3, attempts.get())
        assertEquals(2.0, meters.counter("rendering.remote.retries", "module", "m").count())
    }

    @Test
    fun `gives up after maxAttempts and rethrows the original failure`() {
        val attempts = AtomicInteger()
        val failure = http(502)
        val thrown = assertThrows<WebClientResponseException> {
            fastGuard().block(retryable = true) { Mono.defer { attempts.incrementAndGet(); Mono.error<String>(failure) } }
        }

        assertSame(failure, thrown)
        assertEquals(3, attempts.get())
    }

    @Test
    fun `non-retryable calls and permanent failures are attempted once`() {
        val attempts = AtomicInteger()
        assertThrows<WebClientResponseException> {
            fastGuard().block(retryable = false) { Mono.defer { attempts.incrementAndGet(); Mono.error<String>(http(503)) } }
        }
        assertEquals(1, attempts.get())

        attempts.set(0)
        assertThrows<WebClientResponseException> {
            fastGuard().block(retryable = true) { Mono.defer { attempts.incrementAndGet(); Mono.error<String>(http(404)) } }
        }
        assertEquals(1, attempts.get())
    }

    @Test
    fun `total budget bounds retries and backoffs`() {
        val guard = RemoteCallGuard(
            "m",
            RemoteCallSettings(
                maxAttempts = 50, initialBackoff = Duration.ofMillis(20), maxBackoff = Duration.ofMillis(20),
                totalBudget = Duration.ofMillis(150), breakerEnabled = false
            )
        )
        val started = System.nanoTime()
        assertThrows<Exception> { guard.block(retryable = true) { Mono.error<String>(http(503)) } }

        assertTrue(Duration.ofNanos(System.nanoTime() - started) < Duration.ofSeconds(2))
    }

    @Test
    fun `breaker opens on infrastructure failures and fast-fails without calling the remote`() {
        val guard = fastGuard(maxAttempts = 1, threshold = 3)
        val calls = AtomicInteger()
        repeat(3) {
            assertThrows<WebClientResponseException> {
                guard.block(retryable = false) { Mono.defer { calls.incrementAndGet(); Mono.error<String>(http(503)) } }
            }
        }

        val thrown = assertThrows<RemoteModuleUnavailableException> {
            guard.block(retryable = false) { Mono.defer { calls.incrementAndGet(); Mono.just("never") } }
        }

        assertTrue(thrown.message!!.contains("module m temporarily unavailable"))
        assertEquals(3, calls.get())
        assertEquals(2.0, meters.get("rendering.remote.circuit.state").tag("module", "m").gauge().value())
        assertEquals(1.0, meters.counter("rendering.remote.calls.rejected", "module", "m", "reason", "circuit_open").count())
    }

    @Test
    fun `4xx answers and pool saturation do not count against the breaker`() {
        val guard = fastGuard(maxAttempts = 1, threshold = 2)
        repeat(5) { assertThrows<WebClientResponseException> { guard.block(false) { Mono.error<String>(http(404)) } } }
        repeat(5) {
            assertThrows<RemoteModuleUnavailableException> {
                guard.block(false) { Mono.error<String>(request(PoolAcquirePendingLimitException(100))) }
            }
        }

        assertEquals(CircuitBreaker.State.CLOSED, guard.breaker!!.state())
    }

    @Test
    fun `bulkhead rejects calls beyond the limit`() {
        val guard = fastGuard(maxConcurrent = 1)
        val release = java.util.concurrent.CountDownLatch(1)
        val first = Thread { guard.block(false) { Mono.fromCallable { release.await(); "a" } } }
        first.start()
        while (meters.get("rendering.remote.calls.inflight").tag("module", "m").gauge().value() < 1.0) Thread.sleep(1)

        val thrown = assertThrows<RemoteModuleUnavailableException> { guard.block(false) { Mono.just("b") } }
        release.countDown()
        first.join()

        assertTrue(thrown.message!!.contains("too many concurrent calls"))
        assertEquals(1.0, meters.counter("rendering.remote.calls.rejected", "module", "m", "reason", "bulkhead_full").count())
        assertEquals("c", guard.block(false) { Mono.just("c") })
    }

    @Test
    fun `pool exceptions are mapped to a readable message`() {
        val guard = fastGuard()
        val pending = assertThrows<RemoteModuleUnavailableException> {
            guard.block(true) { Mono.error<String>(request(PoolAcquirePendingLimitException(100))) }
        }
        assertTrue(pending.message!!.contains("connection pool saturated"))

        val timeout = assertThrows<RemoteModuleUnavailableException> {
            guard.block(true) { Mono.error<String>(request(PoolAcquireTimeoutException(Duration.ofSeconds(60)))) }
        }
        assertTrue(timeout.message!!.contains("timed out waiting for a free connection"))
    }

    @Test
    fun `disabled resilience is a pass-through`() {
        val guard = RemoteCallGuard("m", RemoteCallSettings(enabled = false))
        val attempts = AtomicInteger()
        repeat(10) {
            assertThrows<WebClientResponseException> {
                guard.block(true) { Mono.defer { attempts.incrementAndGet(); Mono.error<String>(http(503)) } }
            }
        }
        assertEquals(10, attempts.get())
    }
}
