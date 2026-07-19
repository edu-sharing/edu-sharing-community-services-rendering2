package org.edu_sharing.rendering.modules.document

import org.edu_sharing.rendering.renderingJob.queue.StandardQueueProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

/** Routing + scaling for the document queue (`app.queue.document.*`). Consumed by [DocumentReceiver]. */
@Component
@ConfigurationProperties("app.queue.document")
class DocumentQueueProperties : StandardQueueProperties()
