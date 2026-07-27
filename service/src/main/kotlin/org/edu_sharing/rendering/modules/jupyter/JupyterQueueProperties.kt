package org.edu_sharing.rendering.modules.jupyter

import org.edu_sharing.rendering.renderingJob.queue.StandardQueueProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

/** Routing + scaling for the jupyter queue (`app.queue.jupyter.*`). Consumed by [JupyterReceiver]. */
@Component
@ConfigurationProperties("app.queue.jupyter")
class JupyterQueueProperties : StandardQueueProperties()
