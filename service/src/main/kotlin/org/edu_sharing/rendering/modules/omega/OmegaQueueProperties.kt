package org.edu_sharing.rendering.modules.omega

import org.edu_sharing.rendering.renderingJob.queue.ImportQueueProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

/**
 * Routing + scaling for the omega import queue (`app.queue.omega.*`). IMPORT — one channel per pod; concurrency
 * (K) sizes the dedicated import HTTP pool + broker prefetch. Consumed by [OmegaReceiver], sized in [OmegaImportConfig].
 */
@Component
@ConfigurationProperties("app.queue.omega")
class OmegaQueueProperties : ImportQueueProperties()
