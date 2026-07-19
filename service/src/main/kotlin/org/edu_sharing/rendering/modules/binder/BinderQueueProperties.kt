package org.edu_sharing.rendering.modules.binder

import org.edu_sharing.rendering.renderingJob.queue.StandardQueueProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

/** Routing + scaling for the binder queue (`app.queue.binder.*`). Consumed by [BinderReceiver]. */
@Component
@ConfigurationProperties("app.queue.binder")
class BinderQueueProperties : StandardQueueProperties()
