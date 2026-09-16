package org.edu_sharing.rendering.renderingJob.queue

import org.springframework.amqp.core.AcknowledgeMode
import org.springframework.amqp.rabbit.config.DirectRabbitListenerContainerFactory
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.listener.DirectMessageListenerContainer
import org.springframework.amqp.rabbit.listener.RabbitListenerContainerFactory
import org.springframework.amqp.support.converter.MessageConverter
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.task.AsyncTaskExecutor
import org.springframework.stereotype.Component

/**
 * Reusable factory for the decoupled **remote** queues' listener containers (sodix, omega, ddb — they
 * resolve a link/reference to externally-hosted material rather than importing it).
 *
 * The container mechanism is identical across the remote modules — MANUAL ack (the [AsyncAckDispatcher]
 * owns the ack/nack once the offloaded work finishes), a single consumer/channel per pod, and a
 * per-consumer `prefetch` of K messages processed on virtual threads — so it lives here once. Each
 * remote module wires its own factory bean with its own K via [create], keeping the per-queue tuning in
 * the module while the generic plumbing stays shared. See [AsyncAckDispatcher] and the module remote configs.
 *
 * Deliberately **no** [org.edu_sharing.rendering.renderingJob.metrics.QueueConsumerMetrics] advice: the
 * listener returns as soon as the work is offloaded, so the advice would only see the hand-off.
 * [AsyncAckDispatcher] updates the `rendering.queue.consumers.active` gauge from the virtual thread instead.
 */
@Component
class RemoteListenerContainerFactorySupport(
    private val rabbitConnectionFactory: ConnectionFactory,
    private val messageConverter: MessageConverter,
    @param:Qualifier("rabbitConsumerExecutor") private val rabbitConsumerExecutor: AsyncTaskExecutor,
) {
    fun create(prefetch: Int): RabbitListenerContainerFactory<DirectMessageListenerContainer> {
        val factory = DirectRabbitListenerContainerFactory()
        factory.setConnectionFactory(rabbitConnectionFactory)
        factory.setMessageConverter(messageConverter)
        factory.setDefaultRequeueRejected(false)
        factory.setTaskExecutor(rabbitConsumerExecutor)
        factory.setPrefetchCount(prefetch)
        // MANUAL: AsyncAckDispatcher owns the ack/nack once the offloaded work completes.
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL)
        // These queues are durable=true (see the queue-durability migration, LegacyQueueCleaner), but a
        // transiently missing queue - e.g. a brief window during that migration itself, or a manual
        // broker-side hiccup - must still not tear the whole container down.
        factory.setMissingQueuesFatal(false)
        // NOT setting shutdownTimeout here (unlike queueListenerContainerFactory): AsyncAckDispatcher.dispatch
        // submits to the executor and returns immediately, so the listener invocation the container's
        // graceful-shutdown wait tracks is already long over by the time the offloaded virtual thread does
        // its actual HTTP call — a longer shutdownTimeout would not extend that call's runway at all. A
        // channel/connection closed mid-call is already handled: the message stays unacked and the broker
        // redelivers it (import processing is idempotent, see AsyncAckDispatcher's doc).
        // Continue the trace across the async queue boundary (reads trace context from message headers).
        factory.setObservationEnabled(true)
        return factory
    }
}
