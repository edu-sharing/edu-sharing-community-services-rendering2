package org.edu_sharing.rendering.modules.av

import org.edu_sharing.rendering.renderingJob.queue.StandardQueueProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

/** Routing + scaling for the audio/video queue (`app.queue.av.*`). Consumed by [AvReceiver]. */
@Component
@ConfigurationProperties("app.queue.av")
class AvQueueProperties : StandardQueueProperties()
