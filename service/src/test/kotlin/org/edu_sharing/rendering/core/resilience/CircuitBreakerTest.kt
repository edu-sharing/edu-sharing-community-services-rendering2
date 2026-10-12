package org.edu_sharing.rendering.core.resilience

import org.edu_sharing.rendering.core.resilience.CircuitBreaker.State
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

class CircuitBreakerTest {
    private var now = 0L
    private fun breaker(threshold: Int = 3, halfOpenCalls: Int = 1) =
        CircuitBreaker("x", threshold, Duration.ofSeconds(30), halfOpenCalls) { now }

    @Test
    fun `opens after the threshold of consecutive failures and rejects`() {
        val breaker = breaker()
        repeat(3) { assertTrue(breaker.tryAcquirePermission()); breaker.onFailure() }

        assertEquals(State.OPEN, breaker.state())
        assertFalse(breaker.tryAcquirePermission())
    }

    @Test
    fun `a success resets the consecutive failure count`() {
        val breaker = breaker()
        repeat(2) { breaker.tryAcquirePermission(); breaker.onFailure() }
        breaker.tryAcquirePermission(); breaker.onSuccess()
        repeat(2) { breaker.tryAcquirePermission(); breaker.onFailure() }

        assertEquals(State.CLOSED, breaker.state())
    }

    @Test
    fun `goes half-open after the open duration and admits only the configured probes`() {
        val breaker = breaker()
        repeat(3) { breaker.tryAcquirePermission(); breaker.onFailure() }
        now += Duration.ofSeconds(29).toNanos()
        assertEquals(State.OPEN, breaker.state())
        assertEquals(Duration.ofSeconds(1), breaker.remainingOpenTime())

        now += Duration.ofSeconds(1).toNanos()
        assertEquals(State.HALF_OPEN, breaker.state())
        assertTrue(breaker.tryAcquirePermission())
        assertFalse(breaker.tryAcquirePermission())
    }

    @Test
    fun `successful probe closes, failed probe re-opens`() {
        val breaker = breaker()
        repeat(3) { breaker.tryAcquirePermission(); breaker.onFailure() }
        now += Duration.ofSeconds(30).toNanos()
        breaker.tryAcquirePermission(); breaker.onSuccess()
        assertEquals(State.CLOSED, breaker.state())

        repeat(3) { breaker.tryAcquirePermission(); breaker.onFailure() }
        now += Duration.ofSeconds(30).toNanos()
        breaker.tryAcquirePermission(); breaker.onFailure()
        assertEquals(State.OPEN, breaker.state())
        assertFalse(breaker.tryAcquirePermission())
    }

    @Test
    fun `an ignored probe frees its slot without a verdict`() {
        val breaker = breaker()
        repeat(3) { breaker.tryAcquirePermission(); breaker.onFailure() }
        now += Duration.ofSeconds(30).toNanos()
        assertTrue(breaker.tryAcquirePermission())
        breaker.onIgnored()

        assertEquals(State.HALF_OPEN, breaker.state())
        assertTrue(breaker.tryAcquirePermission())
    }
}
