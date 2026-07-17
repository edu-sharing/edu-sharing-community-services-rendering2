package org.edu_sharing.rendering.modules.onyx

import org.edu_sharing.rendering.renderingJob.queue.StandardQueueProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

/** Routing + scaling for the onyx queue (`app.queue.onyx.*`). Consumed by [OnyxReceiver]. */
@Component
@ConfigurationProperties("app.queue.onyx")
class OnyxQueueProperties : StandardQueueProperties()
