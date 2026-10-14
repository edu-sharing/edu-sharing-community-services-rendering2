package org.edu_sharing.rendering.core.resilience

import java.time.Duration

/**
 * Minimal circuit breaker over consecutive infrastructure failures: `CLOSED` -> (threshold failures) -> `OPEN`
 * -> (after [openDuration]) -> `HALF_OPEN` -> success: `CLOSED`, failure: `OPEN` again.
 *
 * Own implementation instead of Resilience4j: the library is not part of the project's BOM/dependencies and the
 * required behaviour is small. Thread-safe; every successful [tryAcquirePermission] must be answered by exactly
 * one of [onSuccess], [onFailure] or [onIgnored] ([RemoteCallGuard] guarantees this).
 */
class CircuitBreaker(
    val name: String,
    private val failureThreshold: Int,
    private val openDuration: Duration,
    private val halfOpenMaxCalls: Int = 1,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    enum class State { CLOSED, OPEN, HALF_OPEN }

    private var state = State.CLOSED
    private var consecutiveFailures = 0
    private var openedAtNanos = 0L
    private var halfOpenInFlight = 0

    @Synchronized
    fun state(): State {
        advanceIfOpenElapsed()
        return state
    }

    /** Remaining time until probe calls are admitted; zero unless OPEN. */
    @Synchronized
    fun remainingOpenTime(): Duration {
        advanceIfOpenElapsed()
        if (state != State.OPEN) return Duration.ZERO
        return Duration.ofNanos((openDuration.toNanos() - (nanoTime() - openedAtNanos)).coerceAtLeast(0))
    }

    /** `false` means the call must be rejected without contacting the remote. */
    @Synchronized
    fun tryAcquirePermission(): Boolean {
        advanceIfOpenElapsed()
        return when (state) {
            State.CLOSED -> true
            State.OPEN -> false
            State.HALF_OPEN -> if (halfOpenInFlight < halfOpenMaxCalls) {
                halfOpenInFlight++
                true
            } else false
        }
    }

    @Synchronized
    fun onSuccess() {
        when (state) {
            State.CLOSED -> consecutiveFailures = 0
            State.HALF_OPEN -> close()
            State.OPEN -> Unit // a call admitted before the breaker opened; its late success proves nothing
        }
    }

    @Synchronized
    fun onFailure() {
        when (state) {
            State.CLOSED -> if (++consecutiveFailures >= failureThreshold) open()
            State.HALF_OPEN -> open()
            State.OPEN -> Unit
        }
    }

    /** The call ended without a verdict about the remote (cancelled, local overload): only frees a probe slot. */
    @Synchronized
    fun onIgnored() {
        if (state == State.HALF_OPEN && halfOpenInFlight > 0) halfOpenInFlight--
    }

    private fun advanceIfOpenElapsed() {
        if (state == State.OPEN && nanoTime() - openedAtNanos >= openDuration.toNanos()) {
            state = State.HALF_OPEN
            halfOpenInFlight = 0
        }
    }

    private fun open() {
        state = State.OPEN
        openedAtNanos = nanoTime()
        consecutiveFailures = 0
        halfOpenInFlight = 0
    }

    private fun close() {
        state = State.CLOSED
        consecutiveFailures = 0
        halfOpenInFlight = 0
    }
}
