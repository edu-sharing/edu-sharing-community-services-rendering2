package org.edu_sharing.rendering.renderingJob.queue

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

/** Routing + scaling for the main rendering job queue (`app.queue.job.*`). Consumed by [JobReceiver]. */
@Component
@ConfigurationProperties("app.queue.job")
class JobQueueProperties : StandardQueueProperties()
