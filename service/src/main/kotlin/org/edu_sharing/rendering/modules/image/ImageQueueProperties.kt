package org.edu_sharing.rendering.modules.image

import org.edu_sharing.rendering.renderingJob.queue.StandardQueueProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

/** Routing + scaling for the image queue (`app.queue.image.*`). Consumed by [ImageReceiver]. */
@Component
@ConfigurationProperties("app.queue.image")
class ImageQueueProperties : StandardQueueProperties()
