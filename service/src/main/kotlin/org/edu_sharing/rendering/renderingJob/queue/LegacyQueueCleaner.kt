package org.edu_sharing.rendering.renderingJob.queue

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.edu_sharing.rendering.core.annotation.ConditionalOnMaster
import org.slf4j.LoggerFactory
import org.springframework.amqp.core.AmqpAdmin
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Deletes the pre-durability-migration ([LegacyQueueCleanupProperties.legacyQueueNames]) queues once
 * they are orphaned, so the `durable=false` -> `durable=true` + rename migration (see the 15
 * `@RabbitListener` declarations, e.g. [org.edu_sharing.rendering.modules.image.ImageReceiver]) needs no
 * manual `rabbitmqctl delete_queue` maintenance window.
 *
 * A queue's old name can't be redeclared in place with a different `durable` flag (RabbitMQ rejects that
 * with `406 PRECONDITION_FAILED`, which `mismatchedQueuesFatal=false` would silently swallow) - so the
 * migration renames each queue (and its routing key, so old- and new-code pods never share a binding
 * during a rolling deploy) instead. That leaves the *old* transient queues behind at the broker once every
 * pod has rolled over to the new name; this component removes them automatically.
 *
 * **Consumer-count-driven, not a timer**: [AmqpAdmin.getQueueInfo] does a passive declare and returns
 * `null` if the queue is already gone, so a queue that was never deleted (or already cleaned up) is a
 * cheap no-op each run. A queue with `consumerCount == 0` means no old-code pod is bound to it anymore -
 * whatever rollout duration that took, this reacts to it directly instead of guessing a wait time (the
 * `terminationGracePeriod` alone can be tens of minutes per pod, see `QueueConfig`'s `shutdownTimeout`).
 * [AmqpAdmin.deleteQueue] with `unused=true` deletes atomically only if the broker itself still agrees no
 * consumer is attached (closes the race against one reconnecting between the check and the delete) and
 * throws instead of silently no-op'ing if that race is lost - caught here, retried next run.
 *
 * **Accepted data loss**: any messages still in a queue once its last consumer is gone are deleted with
 * it - nothing will ever consume them again regardless of how long cleanup waits, since no code binds to
 * the old name anymore. The corresponding Mongo job stays QUEUED and is caught by
 * [org.edu_sharing.rendering.renderingJob.StaleJobReaper]'s QUEUED safety net, same as any other lost
 * publish.
 */
@Component
@ConditionalOnMaster
@ConditionalOnProperty(name = ["app.queue-migration.enabled"], havingValue = "true", matchIfMissing = true)
class LegacyQueueCleaner(
    private val amqpAdmin: AmqpAdmin,
    private val properties: LegacyQueueCleanupProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(
        fixedDelayString = $$"${app.queue-migration.schedule}",
        initialDelayString = $$"${app.queue-migration.schedule}",
    )
    @SchedulerLock(name = "legacyQueueCleaner", lockAtMostFor = "10m", lockAtLeastFor = "30s")
    fun cleanup() {
        properties.legacyQueueNames.forEach { name ->
            runCatching { cleanupQueue(name) }
                .onFailure { log.warn("Failed to clean up legacy queue '$name', will retry next run", it) }
        }
    }

    private fun cleanupQueue(name: String) {
        val info = amqpAdmin.getQueueInfo(name) ?: return
        if (info.consumerCount > 0) {
            log.debug("Legacy queue '$name' still has ${info.consumerCount} consumer(s); not yet safe to delete")
            return
        }
        if (info.messageCount > 0) {
            log.warn(
                "Deleting orphaned legacy queue '$name' with ${info.messageCount} unconsumed message(s) still " +
                    "in it (no consumer will ever attach to it again) - the affected job(s) will be reconciled " +
                    "by the stale-job reaper's QUEUED safety net"
            )
        } else {
            log.info("Deleting orphaned, empty legacy queue '$name'")
        }
        // unused=true: atomic broker-side re-check against a consumer reconnecting between getQueueInfo and
        // here; throws AmqpIOException on that race instead of the 1-arg overload's silent no-op, which
        // runCatching in cleanup() turns into a retry on the next scheduled run.
        amqpAdmin.deleteQueue(name, true, false)
    }
}
