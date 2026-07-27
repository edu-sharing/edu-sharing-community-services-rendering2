package org.edu_sharing.rendering.renderingJob.metrics

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
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
 * Neben dem momentanen Sättigungssignal misst der Advice pro Queue einen [Timer]: dessen `count`
 * ist das eigentliche **Durchsatz**-Signal (`rate()` = verarbeitete Nachrichten/s), das ein Gauge
 * bei kurzlebigen Handlern (z. B. der Job-Annahme + Sub-Job-Fanout im
 * [org.edu_sharing.rendering.renderingJob.queue.JobReceiver]) systematisch verfehlt, weil ein
 * Scrape fast nie einen gerade laufenden Consumer erwischt. `sum`/`max`/Perzentile liefern
 * zusätzlich die Verarbeitungsdauer.
 *
 * Emittierte Meter:
 * - `rendering.queue.consumers.active{queue}` — gerade verarbeitete Nachrichten dieser Queue/Instanz.
 * - `rendering.queue.processing{queue}` — Timer über die Listener-Ausführung; `count` = Durchsatz.
 */
@Component
class QueueConsumerMetrics(
    private val meterRegistry: MeterRegistry,
) : MethodInterceptor {
    private val log = LoggerFactory.getLogger(javaClass)

    private val activeByQueue = ConcurrentHashMap<String, AtomicInteger>()
    private val timerByQueue = ConcurrentHashMap<String, Timer>()

    override fun invoke(invocation: MethodInvocation): Any? {
        val queue = invocation.arguments
            .filterIsInstance<Message>()
            .firstOrNull()
            ?.messageProperties
            ?.consumerQueue
            ?: return invocation.proceed()

        val active = activeByQueue.computeIfAbsent(queue, ::registerGauge)
        active.incrementAndGet()
        val sample = Timer.start(meterRegistry)
        try {
            return invocation.proceed()
        } finally {
            active.decrementAndGet()
            sample.stop(timerByQueue.computeIfAbsent(queue, ::registerTimer))
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

    /**
     * Times [work] into the same `rendering.queue.processing` timer [invoke] feeds, for callers whose
     * listener container carries no advice chain (e.g.
     * [org.edu_sharing.rendering.renderingJob.queue.AsyncAckDispatcher]) and therefore never traverses
     * [invoke] itself.
     */
    fun <T> timeProcessing(queue: String, work: () -> T): T {
        val sample = Timer.start(meterRegistry)
        try {
            return work()
        } finally {
            sample.stop(timerByQueue.computeIfAbsent(queue, ::registerTimer))
        }
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

    private fun registerTimer(queue: String): Timer {
        log.debug("Registering processing timer for queue '{}'", queue)
        return Timer.builder("rendering.queue.processing")
            .description("Listener processing time per message; count is this queue's throughput")
            .tag("queue", queue)
            .publishPercentileHistogram()
            .register(meterRegistry)
    }
}
