package org.edu_sharing.rendering.renderingJob.queue

/**
 * Per-queue configuration for a lightweight API-passthrough queue, bound from `app.queue.<queue>.*`
 * by a module-local subclass annotated with `@ConfigurationProperties` (e.g.
 * `SodixQueueProperties`). Consumed by the module's [TunableQueueContainerConfig] to size the
 * listener container.
 *
 * Only the settings the container customizer needs are modelled here: [name] (also the key the
 * customizer matches the container on), [prefetch], and the [scaling] profile. `key` and
 * `concurrency` are intentionally NOT modelled — they are consumed only as SpEL placeholders
 * inside the module's `@RabbitListener` (annotation attributes cannot read an injected object),
 * so they stay flat under the same `app.queue.<queue>` prefix and are ignored by this binding.
 */
open class QueueProperties {
    /** Resolved queue name (e.g. `sodix_job_queue`); the key the container customizer matches on. */
    lateinit var name: String

    /** Unacked messages buffered per consumer. `> 1` removes the per-message broker round-trip. */
    var prefetch: Int = 1

    /** This queue's burst auto-scaling profile. */
    var scaling: BurstScaling = BurstScaling()
}

/**
 * One queue's burst auto-scaling knobs (bound from `app.queue.<queue>.scaling.*`). Every knob is
 * nullable and left at the Spring AMQP framework default when unset (start 10000 ms, stop
 * 60000 ms, both triggers 10). See [TunableQueueContainerConfig] for how each is applied.
 */
class BurstScaling {
    /** Min delay (ms) between starting consumers while scaling up. Lower = faster ramp-up on bursts. */
    var startConsumerMinInterval: Long? = null

    /** Min delay (ms) between stopping consumers while scaling down. Lower = faster teardown after a burst. */
    var stopConsumerMinInterval: Long? = null

    /** Consecutive busy receive cycles before a consumer is added. Lower = more eager scale-up. */
    var consecutiveActiveTrigger: Int? = null

    /** Consecutive idle receive cycles before a consumer is removed. Lower = more eager scale-down. */
    var consecutiveIdleTrigger: Int? = null
}
