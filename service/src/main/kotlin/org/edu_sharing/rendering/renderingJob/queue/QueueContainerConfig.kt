package org.edu_sharing.rendering.renderingJob.queue

import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer

/**
 * Per-queue, module-local customization of the RabbitMQ listener container.
 *
 * A module contributes one bean per queue it wants to tune (prefetch, requeue policy,
 * batch size, …). [QueueConfig.queueListenerContainerFactory] collects every such bean and
 * applies [customize] to the container bound to [queueName]; queues without a matching bean
 * keep the factory defaults (prefetch 1). This keeps the shared factory agnostic of the
 * individual modules — each module owns its own queue tuning, gated by the same role
 * conditional as its receiver.
 */
interface QueueContainerConfig {
    /** The resolved queue name this config applies to (e.g. `sodix_job_queue`). */
    val queueName: String

    /** Adjust the listener container that consumes [queueName]. */
    fun customize(container: SimpleMessageListenerContainer)
}
