package org.edu_sharing.rendering.renderingJob.queue

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

/**
 * How a queue is consumed. The [mode] is the single, DevOps-visible switch that replaces the
 * previously scattered knowledge (hard-coded `x-single-active-consumer`, the separate
 * `app.module.<x>.import.*` namespace):
 *
 * - [STANDARD]      — a plain [org.springframework.amqp.rabbit.listener.DirectMessageListenerContainer]
 *   queue on the shared `queueListenerContainerFactory`. [QueueSpec.concurrency] maps 1:1 to
 *   `consumersPerQueue`.
 * - [IMPORT]        — a decoupled high-fan-out import queue (sodix/omega/ddb): one channel per pod,
 *   [QueueSpec.concurrency] = K sizes the dedicated import HTTP pool AND the broker prefetch, and up to
 *   K messages run in parallel on virtual threads via [AsyncAckDispatcher]. Scale via pods, not K.
 * - [SINGLE_ACTIVE] — the queue is declared with `x-single-active-consumer` so exactly one consumer is
 *   active cluster-wide (moodle/h5p, whose downstream imports one file at a time). Concurrency is forced
 *   to 1.
 */
enum class QueueMode { STANDARD, IMPORT, SINGLE_ACTIVE }

/**
 * The full, typed tuning surface of a single queue — everything a DevOps needs to reason about it lives
 * in one block (`app.queue.<queue>.*`): its routing ([name]/[key]), its [mode], and the one authoritative
 * scaling knob [concurrency] ("max messages processed in parallel per pod for this queue"). The derived
 * accessors encapsulate the per-mode semantics so the receivers and container factories never special-case.
 */
class QueueSpec {
    lateinit var name: String
    lateinit var key: String

    var mode: QueueMode = QueueMode.STANDARD

    /**
     * The one scaling knob, uniform across modes: max messages processed in parallel per pod for this
     * queue. See [effectiveConcurrency] for how [SINGLE_ACTIVE] overrides it.
     */
    var concurrency: Int = 1

    /**
     * Broker prefetch, only meaningful for [IMPORT] queues (they own a dedicated listener factory).
     * `null` ⇒ defaults to [concurrency]. STANDARD/SINGLE_ACTIVE queues share the global `app.queue.prefetch`
     * (the shared factory's prefetch applies to all its containers).
     */
    var prefetch: Int? = null

    /**
     * Consumers (channels) registered per queue on the `DirectMessageListenerContainer` — the value fed to
     * `@RabbitListener.concurrency`. Only [STANDARD] scales its consumer count with [concurrency]. [IMPORT]
     * keeps a single channel per pod (its K parallelism comes from prefetch + virtual threads via
     * [AsyncAckDispatcher], not from more consumers), and [SINGLE_ACTIVE] is a single consumer by definition.
     */
    val effectiveConcurrency: Int
        get() = if (mode == QueueMode.STANDARD) concurrency else 1

    /** Source of the `x-single-active-consumer` queue argument's value (true only for [SINGLE_ACTIVE]). */
    val singleActiveConsumer: Boolean
        get() = mode == QueueMode.SINGLE_ACTIVE

    /**
     * Prefetch for an [IMPORT] queue: [prefetch] if set (to run above [concurrency] while jobs spend time in
     * post-call Mongo/S3 work), else [concurrency] so a prefetch slot and an HTTP connection align 1:1.
     */
    val effectiveImportPrefetch: Int
        get() = prefetch ?: concurrency
}

/**
 * The single, typed source of truth for every queue's routing and scaling. Bound from `app.queue.*`.
 *
 * Deliberately **fixed fields, not a `Map<String, QueueSpec>`**: `app.queue.*` also carries scalars
 * ([topicExchange], [controllerBroadcastExchange], [prefetch]) and unrelated sub-trees (`app.queue.minio.*`),
 * which a map binding at this prefix would choke on. Fixed fields also give compile-time-checked SpEL
 * access from the `@RabbitListener` annotations, e.g. `#{queueProperties.image.concurrency}`.
 */
@Component
@ConfigurationProperties("app.queue")
class QueueProperties {

    private val log = LoggerFactory.getLogger(javaClass)

    lateinit var topicExchange: String
    lateinit var controllerBroadcastExchange: String

    /** Global prefetch for all STANDARD/SINGLE_ACTIVE queues (the shared factory's prefetch is not per-queue). */
    var prefetch: Int = 1

    var job = QueueSpec()
    var image = QueueSpec()
    var av = QueueSpec()
    var moodle = QueueSpec()
    var eduHtml = QueueSpec()
    var h5p = QueueSpec()
    var document = QueueSpec()
    var jupyter = QueueSpec()
    var onyx = QueueSpec()
    var binder = QueueSpec()
    var binderPreview = QueueSpec()
    var sodix = QueueSpec()
    var omega = QueueSpec()
    var ddb = QueueSpec()

    fun all(): Map<String, QueueSpec> = mapOf(
        "job" to job, "image" to image, "av" to av, "moodle" to moodle,
        "eduHtml" to eduHtml, "h5p" to h5p, "document" to document, "jupyter" to jupyter,
        "onyx" to onyx, "binder" to binder, "binderPreview" to binderPreview,
        "sodix" to sodix, "omega" to omega, "ddb" to ddb,
    )

    /**
     * Fail fast on a misconfigured tuning surface. Only moodle/h5p may be [QueueMode.SINGLE_ACTIVE] — those
     * are the only receivers that physically declare the `x-single-active-consumer` argument, so declaring
     * any other queue SINGLE_ACTIVE would be a silent no-op. IMPORT prefetch must not sit below concurrency
     * (idle HTTP connections otherwise).
     */
    @PostConstruct
    fun validate() {
        val singleActiveAllowed = setOf("moodle", "h5p")
        all().forEach { (queue, spec) ->
            require(spec.mode != QueueMode.SINGLE_ACTIVE || queue in singleActiveAllowed) {
                "Queue '$queue' must not use mode SINGLE_ACTIVE — only $singleActiveAllowed declare the " +
                    "x-single-active-consumer argument; SINGLE_ACTIVE on any other queue is a silent no-op."
            }
            if (spec.mode == QueueMode.IMPORT) {
                val prefetch = spec.prefetch
                require(prefetch == null || prefetch >= spec.concurrency) {
                    "Import queue '$queue' prefetch ($prefetch) must be >= concurrency (${spec.concurrency}), " +
                        "or HTTP connections sit idle."
                }
            }
            if (spec.mode == QueueMode.SINGLE_ACTIVE && spec.concurrency > 1) {
                log.warn(
                    "Queue '{}' is SINGLE_ACTIVE; configured concurrency {} is ignored (forced to 1).",
                    queue, spec.concurrency,
                )
            }
        }
    }
}
