package org.edu_sharing.rendering.modules.binder

import org.edu_sharing.rendering.renderingJob.queue.StandardQueueProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

/**
 * Routing + scaling for the binder-preview queue (`app.queue.binder-preview.*`). Consumed by [BinderPreviewReceiver].
 * The prefix is kebab-case because Spring Boot rejects camelCase `@ConfigurationProperties` prefixes.
 */
@Component
@ConfigurationProperties("app.queue.binder-preview")
class BinderPreviewQueueProperties : StandardQueueProperties()
