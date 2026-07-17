package org.edu_sharing.rendering.renderingJob.queue

/**
 * Shared, typed config for a decoupled import queue's per-pod concurrency. Each import module
 * (sodix, omega, ddb) binds its own subclass under its own `app.module.<x>.import` prefix, so the
 * two coupled knobs — the outbound HTTP pool size and the broker prefetch — are steered together
 * **per queue** instead of from a single shared value.
 *
 * [concurrency] (K) is the authoritative knob: it sizes the dedicated Reactor Netty pool
 * (`maxConnections`, applied per remote host) — the hard ceiling on parallel API calls per pod — and,
 * by default, the broker prefetch as well. A prefetch slot is held for the whole job (dequeue → HTTP →
 * Mongo/S3 → ack) while a connection is only held during the HTTP part, so the two align 1:1 by default.
 * Set [prefetch] explicitly only to run it *above* [concurrency] (to keep the HTTP pool saturated while
 * jobs spend time in post-call Mongo/S3 work); it must never be lower, or connections sit idle.
 * Cluster throughput scales with pods × K, not with K itself.
 */
open class ImportConcurrencyProperties {
    var concurrency: Int = 50
    var prefetch: Int? = null

    val effectivePrefetch: Int
        get() = prefetch ?: concurrency
}
