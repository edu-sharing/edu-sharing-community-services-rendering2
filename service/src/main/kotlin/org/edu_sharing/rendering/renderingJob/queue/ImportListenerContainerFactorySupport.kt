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
 * Reusable factory for the decoupled **import** queues' listener containers (sodix, omega, ddb).
 *
 * The container mechanism is identical across the import modules — MANUAL ack (the [AsyncAckDispatcher]
 * owns the ack/nack once the offloaded work finishes), a single consumer/channel per pod, and a
 * per-consumer `prefetch` of K messages processed on virtual threads — so it lives here once. Each
 * import module wires its own factory bean with its own K via [create], keeping the per-queue tuning in
 * the module while the generic plumbing stays shared. See [AsyncAckDispatcher] and the module import configs.
 *
 * Deliberately **no** [org.edu_sharing.rendering.renderingJob.metrics.QueueConsumerMetrics] advice: the
 * listener returns as soon as the work is offloaded, so the advice would only see the hand-off.
 * [AsyncAckDispatcher] updates the `rendering.queue.consumers.active` gauge from the virtual thread instead.
 */
@Component
class ImportListenerContainerFactorySupport(
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
        // durable=false / anonymous queues are re-declared on (re)connect; a transiently missing queue
        // must not tear the container down.
        factory.setMissingQueuesFatal(false)
        // Continue the trace across the async queue boundary (reads trace context from message headers).
        factory.setObservationEnabled(true)
        return factory
    }
}
