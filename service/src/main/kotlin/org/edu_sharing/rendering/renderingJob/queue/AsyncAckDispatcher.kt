package org.edu_sharing.rendering.renderingJob.queue

import com.rabbitmq.client.Channel
import org.edu_sharing.rendering.renderingJob.metrics.QueueConsumerMetrics
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.task.AsyncTaskExecutor
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Decouples message *consumption* from message *processing* for the high-fan-out import queues.
 *
 * The [org.springframework.amqp.rabbit.listener.DirectMessageListenerContainer] invokes a listener
 * synchronously on the broker's dispatch thread and ties concurrency 1:1 to consumers (channels). To
 * process many messages per channel — and let the virtual-thread executor actually pay off — the import
 * receivers hand each delivery here: the work runs on [executor] (virtual threads) and the message is
 * acked only after it completes. With MANUAL ack and a per-channel `prefetch` of K, a single consumer
 * keeps up to K messages in flight on K virtual threads while occupying just one channel — so the
 * broker-wide channel budget stays flat while throughput scales with pods × K.
 *
 * Acks/nacks are serialized per channel: the RabbitMQ [Channel] is not thread-safe, and here many
 * virtual threads complete against the same channel concurrently. A [ReentrantLock] (rather than
 * `synchronized`) avoids pinning the virtual thread's carrier during the acknowledgement.
 */
@Component
class AsyncAckDispatcher(
    @param:Qualifier("rabbitConsumerExecutor") private val executor: AsyncTaskExecutor,
    private val metrics: QueueConsumerMetrics,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // Bounded in practice by the number of live consumer channels (consumersPerQueue=1 × import queues,
    // i.e. a handful per pod); reconnects add a few stale entries over the pod's lifetime — negligible.
    private val channelLocks = ConcurrentHashMap<Channel, ReentrantLock>()

    /**
     * Run [work] on a virtual thread, then acknowledge the delivery.
     *
     * `work` is expected to handle its own business errors (marking the job FAILED); returning normally —
     * whether success or handled failure — acks the message. Only an *unhandled* throwable nacks it
     * (requeue=false, matching the containers' `defaultRequeueRejected=false`) to avoid a poison-message
     * redelivery loop; the orphaned DB job is then reconciled by the stale-job reaper. A hard pod crash
     * reaches neither branch, so the still-unacked message is redelivered by the broker (at-least-once —
     * import processing is idempotent, see the sodix/omega/ddb audit).
     */
    fun dispatch(channel: Channel, deliveryTag: Long, queue: String, work: () -> Unit) {
        executor.execute {
            metrics.started(queue)
            try {
                work()
                acknowledge(channel, deliveryTag) { it.basicAck(deliveryTag, false) }
            } catch (throwable: Throwable) {
                log.error("Unhandled error processing delivery $deliveryTag on queue '$queue', dropping message", throwable)
                acknowledge(channel, deliveryTag) { it.basicNack(deliveryTag, false, false) }
            } finally {
                metrics.finished(queue)
            }
        }
    }

    private inline fun acknowledge(channel: Channel, deliveryTag: Long, op: (Channel) -> Unit) {
        try {
            channelLocks.computeIfAbsent(channel) { ReentrantLock() }.withLock { op(channel) }
        } catch (e: Exception) {
            // Channel already closed (shutdown/reconnect): the delivery stays unacked and the broker
            // redelivers it. Nothing to recover here.
            log.warn("Failed to (n)ack delivery $deliveryTag, channel likely closed: ${e.message}")
        }
    }
}
