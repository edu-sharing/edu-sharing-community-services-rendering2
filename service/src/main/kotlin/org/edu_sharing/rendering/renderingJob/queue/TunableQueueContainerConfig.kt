package org.edu_sharing.rendering.renderingJob.queue

import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer

/**
 * Convenience base for module-local tuning of a queue's listener container: the prefetch count
 * plus the auto-scaling behaviour of the [SimpleMessageListenerContainer] (the `min-max`
 * `concurrency` range set on the `@RabbitListener`).
 *
 * Lightweight API-passthrough modules (one short call per message) extend this with their
 * resolved queue name, prefetch value, and their own [BurstScaling] profile (taken from the
 * module's [QueueProperties]). `prefetch > 1` removes the per-message broker round-trip
 * between the short jobs; the scaling profile controls how fast the container adds/removes
 * consumer threads within its `min-max` range:
 *
 * - [BurstScaling.startConsumerMinInterval] / [BurstScaling.stopConsumerMinInterval] — min delay
 *   (ms) between starting resp. stopping a consumer. Lower start / higher stop = faster ramp-up
 *   and slower teardown.
 * - [BurstScaling.consecutiveActiveTrigger] / [BurstScaling.consecutiveIdleTrigger] — how many
 *   consecutive busy resp. idle receive cycles trigger adding resp. removing one consumer.
 *
 * Any knob left unset stays at the Spring AMQP framework default. Modules that need entirely
 * different tuning implement [QueueContainerConfig] directly.
 */
abstract class TunableQueueContainerConfig(
    override val queueName: String,
    private val prefetch: Int = 1,
    private val scaling: BurstScaling = BurstScaling(),
) : QueueContainerConfig {
    override fun customize(container: SimpleMessageListenerContainer) {
        container.setPrefetchCount(prefetch)
        scaling.startConsumerMinInterval?.let(container::setStartConsumerMinInterval)
        scaling.stopConsumerMinInterval?.let(container::setStopConsumerMinInterval)
        scaling.consecutiveActiveTrigger?.let(container::setConsecutiveActiveTrigger)
        scaling.consecutiveIdleTrigger?.let(container::setConsecutiveIdleTrigger)
    }
}
