package org.edu_sharing.rendering.renderingJob.queue

import com.rabbitmq.client.AMQP
import com.rabbitmq.client.ShutdownSignalException
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.edu_sharing.rendering.core.annotation.ConditionalOnMaster
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.core.RabbitOperations
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
 * **Consumer-count-driven, not a timer**: each run issues a plain `queue.delete` with `if-unused=true` per legacy
 * name. The broker deletes atomically only if no consumer is attached anymore - whatever rollout duration that took,
 * this reacts to it directly instead of guessing a wait time (the `terminationGracePeriod` alone can be tens of
 * minutes per pod, see `QueueConfig`'s `shutdownTimeout`). A queue that still has a consumer answers
 * `406 PRECONDITION_FAILED`, which is caught here and retried next run.
 *
 * **No existence probe**: a passive declare (`AmqpAdmin.getQueueInfo`) of a missing queue closes the channel with
 * `404 NOT_FOUND`, and RabbitMQ logs every such channel close at error level - once the migration had completed that
 * produced one broker error per legacy name and run, forever. RabbitMQ answers a `queue.delete` of a missing queue
 * with a plain `delete-ok` instead (see `LegacyQueueCleanerBrokerTest`), so the steady state is silent on both sides.
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
    private val rabbitOperations: RabbitOperations,
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
            runCatching { cleanupQueue(name) }.onFailure {
                if (it.isQueueInUse()) {
                    log.debug("Legacy queue '$name' still has consumer(s); not yet safe to delete")
                } else {
                    log.warn("Failed to clean up legacy queue '$name', will retry next run", it)
                }
            }
        }
    }

    private fun cleanupQueue(name: String) {
        // if-unused=true: the broker itself refuses (406) while an old-code pod is still consuming. A missing queue
        // answers delete-ok with 0 messages, indistinguishable from an empty one - which is all this needs.
        val deletedMessages = rabbitOperations.execute { channel -> channel.queueDelete(name, true, false).messageCount } ?: 0
        if (deletedMessages > 0) {
            log.warn(
                "Deleted orphaned legacy queue '$name' with $deletedMessages unconsumed message(s) still in it (no " +
                    "consumer will ever attach to it again) - the affected job(s) will be reconciled by the stale-job " +
                    "reaper's QUEUED safety net"
            )
        } else {
            log.debug("Legacy queue '$name' is deleted (or was already gone)")
        }
    }

    private fun Throwable.isQueueInUse(): Boolean =
        generateSequence(this) { it.cause }
            .filterIsInstance<ShutdownSignalException>()
            .any { (it.reason as? AMQP.Channel.Close)?.replyCode == AMQP.PRECONDITION_FAILED }
}
