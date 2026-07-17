package org.edu_sharing.rendering.renderingJob.metrics

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.aopalliance.intercept.MethodInterceptor
import org.aopalliance.intercept.MethodInvocation
import org.slf4j.LoggerFactory
import org.springframework.amqp.core.Message
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Zählt pro Queue die **aktuell auf dieser Instanz verarbeiteten** Nachrichten ("aktive
 * Consumer-Threads") und macht sie als Micrometer-Gauge sichtbar.
 *
 * Hängt als Advice in die Advice-Chain der Listener-Container ein (siehe `QueueConfig`): der
 * Interceptor umschließt jede Listener-Ausführung, erhöht beim Eintritt den Zähler der Ziel-Queue
 * und senkt ihn im `finally` wieder. Seit dem Umstieg auf den
 * [org.springframework.amqp.rabbit.listener.DirectMessageListenerContainer] gibt es keine
 * Autoscale-Kurve mehr und die Consumer teilen sich einen Virtual-Thread-Executor — die Zahl der
 * *gerade* verarbeitenden Consumer ist damit das aussagekräftige Instanz-Sättigungssignal. Die
 * autoritative Queue-Tiefe kommt weiterhin aus dem RabbitMQ-Prometheus-Plugin (Port 15692).
 *
 * Emittierte Meter:
 * - `rendering.queue.consumers.active{queue}` — gerade verarbeitete Nachrichten dieser Queue/Instanz.
 */
@Component
class QueueConsumerMetrics(
    private val meterRegistry: MeterRegistry,
) : MethodInterceptor {
    private val log = LoggerFactory.getLogger(javaClass)

    private val activeByQueue = ConcurrentHashMap<String, AtomicInteger>()

    override fun invoke(invocation: MethodInvocation): Any? {
        val queue = invocation.arguments
            .filterIsInstance<Message>()
            .firstOrNull()
            ?.messageProperties
            ?.consumerQueue
            ?: return invocation.proceed()

        val active = activeByQueue.computeIfAbsent(queue, ::registerGauge)
        active.incrementAndGet()
        try {
            return invocation.proceed()
        } finally {
            active.decrementAndGet()
        }
    }

    /**
     * Increment the active-consumer gauge for [queue]. Used by the async import path
     * ([org.edu_sharing.rendering.renderingJob.queue.AsyncAckDispatcher]) where processing runs on a
     * virtual thread *after* the listener returns, so the advice-chain interceptor above would only see
     * the (instant) hand-off, not the real in-flight work. Pair every call with [finished].
     */
    fun started(queue: String) {
        activeByQueue.computeIfAbsent(queue, ::registerGauge).incrementAndGet()
    }

    /** Decrement the active-consumer gauge for [queue]; counterpart to [started]. */
    fun finished(queue: String) {
        activeByQueue[queue]?.decrementAndGet()
    }

    private fun registerGauge(queue: String): AtomicInteger {
        log.debug("Registering active-consumer gauge for queue '{}'", queue)
        val active = AtomicInteger(0)
        Gauge.builder("rendering.queue.consumers.active", active) { it.get().toDouble() }
            .description("Messages currently being processed for this queue on this instance")
            .tag("queue", queue)
            .register(meterRegistry)
        return active
    }
}
