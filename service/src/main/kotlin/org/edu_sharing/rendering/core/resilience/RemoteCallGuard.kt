package org.edu_sharing.rendering.core.resilience

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.netty.channel.ConnectTimeoutException
import io.netty.handler.timeout.ReadTimeoutException
import org.slf4j.LoggerFactory
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import java.io.IOException
import java.time.Duration
import java.util.concurrent.Semaphore
import javax.net.ssl.SSLException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min

/** The remote module cannot be called right now (open circuit, saturated bulkhead or connection pool). */
class RemoteModuleUnavailableException(
    message: String,
    cause: Throwable? = null,
    /** When the call is worth repeating (remaining open time of the breaker), if known; drives `Retry-After`. */
    val retryAfter: Duration? = null,
) : RuntimeException(message, cause, false, cause != null)

/**
 * Wraps the calls to one external system with a bulkhead, a circuit breaker and (for idempotent calls) a retry
 * with exponential backoff and jitter. Obtain instances from [RemoteCallGuardRegistry].
 *
 * Contract of [run]:
 * - The breaker and the bulkhead guard the whole call; the outcome is recorded once per call, after retries.
 * - Only infrastructure failures count against the breaker ([isInfrastructureFailure]); a 4xx proves the remote
 *   is alive and counts as success. A guard may narrow this to reachability failures only
 *   ([FailureClassification.REACHABILITY_ONLY], per-guard setting): then a 500/507 is a success as well.
 * - Retries happen only if the caller declares the call retryable (idempotent GET/HEAD) and the failure is
 *   transient ([isTransient]). Retrying a timed-out call is deliberately excluded: it would multiply the stall.
 * - The whole call, retries and backoffs included, is capped by [RemoteCallSettings.totalBudget].
 * - Reactor Netty pool exhaustion is mapped to a readable [RemoteModuleUnavailableException].
 */
class RemoteCallGuard(
    val name: String,
    private val settings: RemoteCallSettings = RemoteCallSettings(),
    meterRegistry: MeterRegistry? = null,
    nanoTime: () -> Long = System::nanoTime,
    private val random: () -> Double = { Math.random() },
) {
    private val log = LoggerFactory.getLogger(javaClass)

    val breaker: CircuitBreaker? =
        if (settings.enabled && settings.breakerEnabled)
            CircuitBreaker(name, settings.failureThreshold, settings.openDuration, settings.halfOpenMaxCalls, nanoTime)
        else null
    private val bulkhead: Semaphore? =
        if (settings.enabled && settings.bulkheadEnabled) Semaphore(settings.maxConcurrentCalls, true) else null
    private val inFlight = AtomicInteger()

    private val rejectedByCircuit: Counter?
    private val rejectedByBulkhead: Counter?
    private val retries: Counter?

    init {
        if (meterRegistry != null) {
            rejectedByCircuit = Counter.builder("rendering.remote.calls.rejected")
                .description("Calls rejected without contacting the remote module")
                .tag("module", name).tag("reason", "circuit_open").register(meterRegistry)
            rejectedByBulkhead = Counter.builder("rendering.remote.calls.rejected")
                .description("Calls rejected without contacting the remote module")
                .tag("module", name).tag("reason", "bulkhead_full").register(meterRegistry)
            retries = Counter.builder("rendering.remote.retries")
                .description("Retried attempts of idempotent calls to a remote module")
                .tag("module", name).register(meterRegistry)
            Gauge.builder("rendering.remote.circuit.state", this) { guard ->
                when (guard.breaker?.state()) {
                    CircuitBreaker.State.OPEN -> 2.0
                    CircuitBreaker.State.HALF_OPEN -> 1.0
                    else -> 0.0
                }
            }.description("Circuit breaker state of a remote module: 0 closed, 1 half-open, 2 open")
                .tag("module", name).register(meterRegistry)
            Gauge.builder("rendering.remote.calls.inflight", inFlight) { it.get().toDouble() }
                .description("Calls to a remote module currently in flight on this pod")
                .tag("module", name).register(meterRegistry)
        } else {
            rejectedByCircuit = null
            rejectedByBulkhead = null
            retries = null
        }
    }

    /**
     * Runs the call produced by [call]. The supplier is invoked once per attempt, so it must build a fresh
     * request each time. [retryable] must only be `true` for idempotent GET/HEAD calls.
     */
    fun <T : Any> run(retryable: Boolean, call: () -> Mono<T>): Mono<T> {
        if (!settings.enabled) return Mono.defer(call).onErrorMap(::mapPoolFailure)
        return Mono.defer {
            if (breaker != null && !breaker.tryAcquirePermission()) {
                rejectedByCircuit?.increment()
                return@defer Mono.error<T>(
                    RemoteModuleUnavailableException(
                        "module $name temporarily unavailable (circuit open, probing again in " +
                            "${breaker.remainingOpenTime().seconds}s)",
                        retryAfter = breaker.remainingOpenTime(),
                    )
                )
            }
            if (bulkhead != null && !acquireBulkhead(bulkhead)) {
                breaker?.onIgnored()
                rejectedByBulkhead?.increment()
                return@defer Mono.error<T>(
                    RemoteModuleUnavailableException(
                        "module $name temporarily unavailable (too many concurrent calls, limit ${settings.maxConcurrentCalls})"
                    )
                )
            }
            inFlight.incrementAndGet()
            val reported = AtomicBoolean()
            var source: Mono<T> = Mono.defer(call)
            if (retryable && settings.maxAttempts > 1) source = source.retryWhen(retrySpec())
            source
                .timeout(settings.totalBudget)
                .doOnSuccess { report(reported, Verdict.SUCCESS) }
                .doOnError { report(reported, classify(it)) }
                .doFinally {
                    // Cancellation (e.g. a caller-side timeout) ends the call without a verdict.
                    report(reported, Verdict.IGNORED)
                    bulkhead?.release()
                    inFlight.decrementAndGet()
                }
                .onErrorMap(::mapPoolFailure)
        }
    }

    /** Blocking variant for the module services, which call from a (virtual) worker thread. */
    fun <T : Any> block(retryable: Boolean, call: () -> Mono<T>): T? = run(retryable, call).block()

    private fun acquireBulkhead(semaphore: Semaphore): Boolean =
        if (settings.bulkheadMaxWait.isZero) semaphore.tryAcquire()
        else semaphore.tryAcquire(settings.bulkheadMaxWait.toNanos(), TimeUnit.NANOSECONDS)

    private enum class Verdict { SUCCESS, FAILURE, IGNORED }

    private fun report(reported: AtomicBoolean, verdict: Verdict) {
        if (breaker == null || !reported.compareAndSet(false, true)) return
        when (verdict) {
            Verdict.SUCCESS -> breaker.onSuccess()
            Verdict.FAILURE -> breaker.onFailure()
            Verdict.IGNORED -> breaker.onIgnored()
        }
    }

    private fun classify(failure: Throwable): Verdict = when {
        isLocalPoolSaturation(failure) -> Verdict.IGNORED
        countsAsFailure(failure) -> Verdict.FAILURE
        // A 4xx or a decoding problem: the remote answered, it is healthy.
        failure is WebClientResponseException -> Verdict.SUCCESS
        else -> Verdict.IGNORED
    }

    private fun countsAsFailure(failure: Throwable): Boolean = when (settings.failureClassification) {
        FailureClassification.DEFAULT -> isInfrastructureFailure(failure)
        FailureClassification.REACHABILITY_ONLY -> isReachabilityFailure(failure)
    }

    private fun retrySpec(): Retry = Retry.from { signals ->
        signals.concatMap { signal ->
            val failure = signal.failure()
            val delay = if (signal.totalRetries() + 1 >= settings.maxAttempts) null
            else retryDelay(signal.totalRetries().toInt(), failure)
            if (delay == null) Mono.error(failure)
            else {
                retries?.increment()
                log.debug("Retrying call to {} in {} ms after: {}", name, delay.toMillis(), failure.javaClass.simpleName)
                Mono.delay(delay)
            }
        }
    }

    /**
     * Delay before retry number [retryIndex] (0-based) or `null` if the failure must not be retried. Exponential,
     * capped at `maxBackoff`, with the upper [RemoteCallSettings.jitter] fraction randomised away. A 429's
     * `Retry-After` raises the delay (never lowers it); one beyond `maxRetryAfter` means no retry.
     */
    internal fun retryDelay(retryIndex: Int, failure: Throwable): Duration? {
        if (!isTransient(failure)) return null
        val exponent = min(retryIndex, 30)
        val base = min(settings.maxBackoff.toNanos().toDouble(), settings.initialBackoff.toNanos().toDouble() * (1L shl exponent))
        var delay = Duration.ofNanos((base * (1.0 - settings.jitter * random())).toLong())
        if (failure is WebClientResponseException && failure.statusCode.value() == 429) {
            val retryAfter = retryAfterOf(failure) ?: return null
            if (retryAfter > settings.maxRetryAfter) return null
            if (retryAfter > delay) delay = retryAfter
        }
        return delay
    }

    private fun mapPoolFailure(failure: Throwable): Throwable {
        val poolFailure = causeChain(failure).firstOrNull { it.javaClass.simpleName in POOL_EXCEPTIONS } ?: return failure
        return RemoteModuleUnavailableException(
            if (poolFailure.javaClass.simpleName == "PoolAcquireTimeoutException")
                "module $name temporarily unavailable (timed out waiting for a free connection)"
            else "module $name temporarily unavailable (connection pool saturated, request queue is full)",
            failure
        )
    }

    companion object {
        private val POOL_EXCEPTIONS = setOf("PoolAcquirePendingLimitException", "PoolAcquireTimeoutException")

        private fun causeChain(failure: Throwable): Sequence<Throwable> =
            generateSequence(failure) { it.cause?.takeIf { cause -> cause !== it } }.take(10)

        /** Our own queue is full: says nothing about the health of the remote. */
        internal fun isLocalPoolSaturation(failure: Throwable): Boolean =
            causeChain(failure).any { it.javaClass.simpleName == "PoolAcquirePendingLimitException" }

        private fun hasTimeoutCause(failure: Throwable): Boolean =
            causeChain(failure).any { it is TimeoutException || it is ReadTimeoutException || it is ConnectTimeoutException }

        /** Failures that count against the circuit breaker: 5xx (not 501), connect/reset, timeouts, pool wait timeout. */
        internal fun isInfrastructureFailure(failure: Throwable): Boolean = when {
            isLocalPoolSaturation(failure) -> false
            failure is WebClientResponseException -> failure.statusCode.is5xxServerError && failure.statusCode.value() != 501
            failure is WebClientRequestException -> true
            hasTimeoutCause(failure) -> true
            causeChain(failure).any { it.javaClass.simpleName == "PoolAcquireTimeoutException" } -> true
            failure is IOException -> true
            else -> false
        }

        /**
         * [FailureClassification.REACHABILITY_ONLY]: like [isInfrastructureFailure], but of the HTTP statuses only
         * 502/503/504 (a gateway or the remote itself is unreachable) count. A 500/507 is an answer of a reachable
         * remote (e.g. one broken piece of content) and must not open the breaker for everything else.
         */
        internal fun isReachabilityFailure(failure: Throwable): Boolean = when {
            isLocalPoolSaturation(failure) -> false
            failure is WebClientResponseException -> failure.statusCode.value() in 502..504
            else -> isInfrastructureFailure(failure)
        }

        /**
         * Failures worth retrying for an idempotent call: 5xx except 501, a 429 (with `Retry-After`, checked when the
         * delay is computed) and request errors such as refused/reset connections or a prematurely closed response.
         * Never retried: 4xx, decoding errors, any timeout (the stall would multiply), TLS errors and pool exhaustion.
         */
        internal fun isTransient(failure: Throwable): Boolean = when {
            isLocalPoolSaturation(failure) -> false
            causeChain(failure).any { it.javaClass.simpleName == "PoolAcquireTimeoutException" } -> false
            hasTimeoutCause(failure) -> false
            failure is WebClientResponseException ->
                failure.statusCode.value() == 429 ||
                    (failure.statusCode.is5xxServerError && failure.statusCode.value() != 501)
            failure is WebClientRequestException -> causeChain(failure).none { it is SSLException }
            else -> false
        }

        private fun retryAfterOf(failure: WebClientResponseException): Duration? =
            failure.headers.getFirst("Retry-After")?.trim()?.toLongOrNull()?.takeIf { it >= 0 }?.let { Duration.ofSeconds(it) }
    }
}
