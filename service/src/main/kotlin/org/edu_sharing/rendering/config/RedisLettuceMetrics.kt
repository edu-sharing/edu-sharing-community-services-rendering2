package org.edu_sharing.rendering.config

import io.lettuce.core.AbstractRedisClient
import io.lettuce.core.RedisChannelHandler
import io.lettuce.core.RedisConnectionStateListener
import io.lettuce.core.event.command.CommandFailedEvent
import io.lettuce.core.event.command.CommandListener
import io.lettuce.core.event.command.CommandStartedEvent
import io.lettuce.core.event.command.CommandSucceededEvent
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.net.SocketAddress
import java.util.concurrent.atomic.AtomicInteger

/**
 * Macht Lettuces **internen Command-Puffer** als Micrometer-Meter sichtbar.
 *
 * Fällt Redis aus, lehnt Lettuce Kommandos nicht ab, sondern nimmt sie an und puffert sie im
 * `DefaultEndpoint.disconnectedBuffer`, bis die Verbindung wieder steht. Weder
 * `ClientOptions.requestQueueSize` noch `disconnectedBehavior` sind hier konfiguriert, dieser
 * Puffer ist also praktisch **unbegrenzt** (`Integer.MAX_VALUE`). Da jeder HTTP-Request Redis
 * anfasst (Spring Session sowie [org.edu_sharing.rendering.security.NodeSessionContextRepository]
 * und [org.edu_sharing.rendering.security.NodePermissionSessionContextRepository]), wächst der
 * Puffer während eines Redis-Ausfalls still im Heap — bisher ohne jedes Signal auf
 * `/actuator/prometheus`.
 *
 * Lettuce hat keinen öffentlichen Getter für diese Puffertiefe (`DefaultEndpoint.queueSize` und
 * `disconnectedBuffer` sind privat; `CommandHandler.getStack()` sieht nur einen *aktiven* Kanal —
 * also genau das, was im Ausfall fehlt). Deshalb zählen wir selbst über die öffentliche
 * [CommandListener]-API: `commandStarted` minus `commandSucceeded`/`commandFailed` ergibt die
 * Zahl der geschriebenen, aber noch nicht abgeschlossenen Kommandos — im Ausfall ist das die
 * Puffertiefe. Die Bilanz kann nicht weglaufen, weil `CommandWrapper` seinen Terminalpfad per
 * CAS absichert (genau ein `commandSucceeded`/`commandFailed` pro Kommando) und `cancel()` über
 * `doOnError(CancellationException)` ebenfalls als `commandFailed` durchschlägt — wichtig, denn
 * `DefaultEndpoint.reset()`/`close()` cancelt gepufferte Kommandos beim Verbindungsverlust.
 *
 * Der Verbindungs-Gauge und die Disconnect-Zähler kommen aus derselben Registrierung
 * ([RedisConnectionStateListener]) und machen den Puffer-Gauge erst interpretierbar: sie zeigen,
 * *ob* Redis tatsächlich weg ist.
 *
 * Registriert wird beides in [RedisConfig] über die überschriebene `createClient()`-Methode der
 * Connection-Factory — Lettuce hängt den `CommandListenerWriter` nur ein, wenn zum
 * Verbindungsaufbau schon Listener registriert sind; später hinzugefügte werden ignoriert.
 *
 * Emittierte Meter:
 * - `rendering.redis.commands.pending` — an Lettuce übergebene, noch nicht abgeschlossene
 *   Kommandos. Im Normalbetrieb ~0; steigt im Redis-Ausfall unbegrenzt an.
 * - `rendering.redis.connections.active` — aktuell verbundene Lettuce-Connections; fällt im
 *   Ausfall auf 0.
 * - `rendering.redis.disconnects` — Counter der Verbindungsabbrüche (zählt auch bei jedem
 *   fehlgeschlagenen Reconnect-Versuch hoch).
 * - `rendering.redis.errors` — Counter der von Lettuce gemeldeten Verbindungsfehler.
 */
@Component
class RedisLettuceMetrics(
    meterRegistry: MeterRegistry,
) : CommandListener, RedisConnectionStateListener {
    private val log = LoggerFactory.getLogger(javaClass)

    private val pending = AtomicInteger(0)
    private val connections = AtomicInteger(0)

    private val disconnects: Counter = Counter.builder("rendering.redis.disconnects")
        .description("Lettuce connections lost; also increments on each failed reconnect attempt")
        .register(meterRegistry)

    private val errors: Counter = Counter.builder("rendering.redis.errors")
        .description("Connection-level errors reported by Lettuce")
        .register(meterRegistry)

    init {
        Gauge.builder("rendering.redis.commands.pending", pending) { it.get().toDouble() }
            .description("Redis commands handed to Lettuce but not yet completed; grows while Redis is unreachable")
            .register(meterRegistry)
        Gauge.builder("rendering.redis.connections.active", connections) { it.get().toDouble() }
            .description("Lettuce connections currently connected to Redis")
            .register(meterRegistry)
    }

    /**
     * Hängt diese Instanz als Command- **und** Connection-State-Listener an [client]. Muss vor dem
     * ersten Verbindungsaufbau passieren (siehe Klassen-KDoc).
     */
    fun bindTo(client: AbstractRedisClient) {
        log.debug("Binding Redis/Lettuce metrics to client {}", client.javaClass.simpleName)
        client.addListener(this as CommandListener)
        client.addListener(this as RedisConnectionStateListener)
    }

    override fun commandStarted(event: CommandStartedEvent) {
        pending.incrementAndGet()
    }

    override fun commandSucceeded(event: CommandSucceededEvent) {
        pending.decrementAndGet()
    }

    override fun commandFailed(event: CommandFailedEvent) {
        pending.decrementAndGet()
    }

    // Nur die Zwei-Argument-Variante überschreiben: Lettuce feuert ausschließlich
    // ConnectionEvents.fireEventRedisConnected(handler, address); die Ein-Argument-Variante ist eine
    // Default-Methode, an die die Zwei-Argument-Default-Implementierung delegiert. Beide zu
    // überschreiben würde doppelt zählen.
    override fun onRedisConnected(connection: RedisChannelHandler<*, *>, socketAddress: SocketAddress) {
        connections.incrementAndGet()
    }

    override fun onRedisDisconnected(connection: RedisChannelHandler<*, *>) {
        connections.decrementAndGet()
        disconnects.increment()
    }

    override fun onRedisExceptionCaught(connection: RedisChannelHandler<*, *>, cause: Throwable) {
        errors.increment()
    }
}
