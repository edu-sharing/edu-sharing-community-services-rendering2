package org.edu_sharing.rendering.edusharingRepo.cache

import org.edu_sharing.rendering.renderingJob.queue.QueueProperties
import org.slf4j.LoggerFactory
import org.springframework.amqp.core.AmqpTemplate
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Tells every other pod that a repository registration changed, so it evicts its cached copy and re-reads it
 * from MongoDB on next use ([RegistrationCacheReceiver]). Present on every role: only the master writes
 * registrations, but all roles read (and cache) them.
 *
 * A failed publish is logged and swallowed: the change is already persisted, and the other pods catch up when
 * their entry expires ([RegistrationCacheProperties.expireAfterWrite]).
 */
@Component
class RegistrationCacheBroadcaster(
    private val amqpTemplate: AmqpTemplate,
    private val queueProperties: QueueProperties
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Identifies this JVM, so a pod can skip the broadcast it sent itself. */
    val instanceId: String = UUID.randomUUID().toString()

    fun registrationChanged(repoId: String) {
        try {
            amqpTemplate.convertAndSend(
                queueProperties.registrationBroadcastExchange,
                "",
                RegistrationChangedMessage(repoId = repoId, origin = instanceId)
            )
        } catch (e: Exception) {
            log.warn("Could not broadcast the change of registration $repoId; other pods pick it up when their cache entry expires", e)
        }
    }
}
