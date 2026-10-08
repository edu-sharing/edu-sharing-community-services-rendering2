package org.edu_sharing.rendering.renderingJob.queue

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

/**
 * The broker-wide scalars shared by every queue, bound from `app.queue.*`. Deliberately holds **no** per-queue
 * knowledge: each queue's routing + scaling lives in its module's own
 * `@ConfigurationProperties("app.queue.<module>")` bean (a [QueueSpec] subclass), so module knowledge stays in
 * the module folder. Receivers reference their own bean from SpEL, e.g. `#{imageQueueProperties.name}`, and this
 * bean only for the truly shared values (`#{queueProperties.topicExchange}`).
 */
@Component
@ConfigurationProperties("app.queue")
class QueueProperties {

    lateinit var topicExchange: String
    lateinit var controllerBroadcastExchange: String

    /**
     * Fanout exchange on which the master announces a changed repository registration, so every pod drops its
     * locally cached copy (see `RegistrationCacheBroadcaster`). Separate from [controllerBroadcastExchange]
     * because that one is consumed by the CORS receiver, which treats every message as "sync".
     */
    var registrationBroadcastExchange: String = "registration_broadcast_exchange"

    /** Global prefetch for all STANDARD/SINGLE_ACTIVE queues (the shared factory's prefetch is not per-queue). */
    var prefetch: Int = 1
}
