package org.edu_sharing.rendering.renderingJob.queue

import jakarta.annotation.PostConstruct

/**
 * How a queue is consumed. The [mode] is not a per-deployment tuning knob — it is a fachlich property of the
 * queue, so it is fixed in code by which base class a module's queue-properties extends ([StandardQueueProperties],
 * [RemoteQueueProperties], [SingleActiveQueueProperties]) rather than read from `app.queue.<x>.mode`:
 *
 * - [STANDARD]      — a plain [org.springframework.amqp.rabbit.listener.DirectMessageListenerContainer]
 *   queue on the shared `queueListenerContainerFactory`. [QueueSpec.concurrency] maps 1:1 to `consumersPerQueue`.
 * - [REMOTE]        — a decoupled high-fan-out queue resolving externally-hosted material (sodix/omega/ddb
 *   only fetch a link/reference to it): one channel per pod, [QueueSpec.concurrency] = K sizes the dedicated
 *   remote HTTP pool AND the broker prefetch, and up to K messages run in parallel on virtual threads via
 *   [AsyncAckDispatcher]. Scale via pods, not K.
 * - [SINGLE_ACTIVE] — the queue is declared with `x-single-active-consumer` so exactly one consumer is
 *   active cluster-wide (the h5p *import* queue, since lumi imports one file at a time; the h5p lookup queue
 *   feeding it is STANDARD). Concurrency is forced to 1.
 */
enum class QueueMode { STANDARD, REMOTE, SINGLE_ACTIVE }

/**
 * The full, typed tuning surface of a single queue — everything a DevOps needs to reason about it lives in one
 * block (`app.queue.<queue>.*`): its routing ([name]/[key]) and the one authoritative scaling knob [concurrency]
 * ("max messages processed in parallel per pod for this queue"). The [mode] is contributed by the concrete
 * subclass, not by configuration. The derived accessors encapsulate the per-mode semantics so the receivers and
 * container factories never special-case.
 *
 * A module owns its queue by declaring a `@ConfigurationProperties("app.queue.<module>")` bean that extends the
 * base class matching its mode — so the module folder, not this shared package, is the single home of that
 * module's queue knowledge.
 */
abstract class QueueSpec {
    lateinit var name: String
    lateinit var key: String

    /** Fixed by the concrete subclass ([StandardQueueProperties]/[RemoteQueueProperties]/[SingleActiveQueueProperties]). */
    abstract val mode: QueueMode

    /**
     * The one scaling knob, uniform across modes: max messages processed in parallel per pod for this queue.
     * See [effectiveConcurrency] for how [SINGLE_ACTIVE] overrides it.
     */
    var concurrency: Int = 1

    /**
     * Broker prefetch, only meaningful for [REMOTE] queues (they own a dedicated listener factory).
     * `null` ⇒ defaults to [concurrency]. STANDARD/SINGLE_ACTIVE queues share the global `app.queue.prefetch`
     * (the shared factory's prefetch applies to all its containers).
     */
    var prefetch: Int? = null

    /**
     * Consumers (channels) registered per queue on the `DirectMessageListenerContainer` — the value fed to
     * `@RabbitListener.concurrency`. Only [STANDARD] scales its consumer count with [concurrency]. [REMOTE]
     * keeps a single channel per pod (its K parallelism comes from prefetch + virtual threads via
     * [AsyncAckDispatcher], not from more consumers), and [SINGLE_ACTIVE] is a single consumer by definition.
     */
    val effectiveConcurrency: Int
        get() = if (mode == QueueMode.STANDARD) concurrency else 1

    /** Source of the `x-single-active-consumer` queue argument's value (true only for [SINGLE_ACTIVE]). */
    val singleActiveConsumer: Boolean
        get() = mode == QueueMode.SINGLE_ACTIVE

    /**
     * Prefetch for a [REMOTE] queue: [prefetch] if set (to run above [concurrency] while jobs spend time in
     * post-call Mongo/S3 work), else [concurrency] so a prefetch slot and an HTTP connection align 1:1.
     */
    val effectiveRemotePrefetch: Int
        get() = prefetch ?: concurrency
}

/** Base for a plain fan-out queue on the shared listener factory. [concurrency] = registered consumers per pod. */
abstract class StandardQueueProperties : QueueSpec() {
    override val mode = QueueMode.STANDARD
}

/**
 * Base for a decoupled high-fan-out queue resolving externally-hosted material (one channel per pod;
 * K = [concurrency] sizes the dedicated remote HTTP pool + broker prefetch). Fails fast if a configured
 * prefetch would leave HTTP connections idle.
 */
abstract class RemoteQueueProperties : QueueSpec() {
    override val mode = QueueMode.REMOTE

    @PostConstruct
    fun validateRemotePrefetch() {
        val prefetch = prefetch
        require(prefetch == null || prefetch >= concurrency) {
            "Remote queue prefetch ($prefetch) must be >= concurrency ($concurrency), or HTTP connections sit idle."
        }
    }
}

/**
 * Base for a queue declared with `x-single-active-consumer` (exactly one consumer cluster-wide). [concurrency]
 * is ignored — [effectiveConcurrency] is forced to 1 — so only queues whose receiver actually declares the
 * `x-single-active-consumer` argument may use this base. Put only the serialized step behind such a queue: the
 * h5p flow keeps its presence lookup on a separate STANDARD queue so it is not serialized along with the import.
 */
abstract class SingleActiveQueueProperties : QueueSpec() {
    override val mode = QueueMode.SINGLE_ACTIVE
}
