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

    /** Global prefetch for all STANDARD/SINGLE_ACTIVE queues (the shared factory's prefetch is not per-queue). */
    var prefetch: Int = 1
}
