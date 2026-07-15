package org.edu_sharing.rendering.renderingJob.metrics

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * Macht die **Ebene-1-Auto-Scaling-Kurve** (Consumer-Threads pro Queue innerhalb dieser Instanz)
 * als Micrometer-Gauges sichtbar — das zentrale Beobachtungsziel für die Queue-Scaling-Settings.
 *
 * RabbitMQ selbst kennt nur die Anzahl offener Channels, nicht die interne Thread-Zahl des
 * [SimpleMessageListenerContainer]; darum wird sie hier periodisch aus dem
 * [RabbitListenerEndpointRegistry] abgetastet. Ergänzt die autoritativen Queue-Tiefen-Metriken aus
 * dem RabbitMQ-Prometheus-Plugin (Port 15692).
 *
 * Emittierte Meter:
 * - `rendering.queue.consumers.active{queue}` — aktuell laufende Consumer-Threads.
 *
 * Die konfigurierte Obergrenze des `concurrency`-Bereichs ist ein statischer Wert aus
 * `application.properties` (siehe `docs/QUEUE_SCALING.md`) und wird bewusst nicht als Gauge
 * dupliziert.
 */
@Component
class QueueConsumerMetrics(
    private val listenerRegistry: RabbitListenerEndpointRegistry,
    private val meterRegistry: MeterRegistry,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val activeByQueue = ConcurrentHashMap<String, Int>()
    private val registered = ConcurrentHashMap.newKeySet<String>()

    /**
     * Tastet im 1-Sekunden-Takt alle Listener-Container ab und aktualisiert die Gauge-Backing-Maps.
     * Neue Queues werden beim ersten Auftreten einmalig als Gauges registriert.
     */
    @Scheduled(fixedRate = 1000)
    fun sample() {
        listenerRegistry.listenerContainers.forEach { container ->
            if (container is SimpleMessageListenerContainer) {
                container.queueNames.forEach { queue ->
                    activeByQueue[queue] = container.activeConsumerCount
                    ensureGauges(queue)
                }
            }
        }
    }

    private fun ensureGauges(queue: String) {
        if (registered.add(queue)) {
            log.debug("Registering consumer gauges for queue '{}'", queue)
            Gauge.builder("rendering.queue.consumers.active") { activeByQueue[queue]?.toDouble() ?: 0.0 }
                .description("Active Spring AMQP consumer threads for this queue on this instance")
                .tag("queue", queue)
                .register(meterRegistry)
        }
    }
}
