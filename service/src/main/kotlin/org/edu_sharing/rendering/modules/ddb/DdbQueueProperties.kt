package org.edu_sharing.rendering.modules.ddb

import org.edu_sharing.rendering.renderingJob.queue.ImportQueueProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

/**
 * Routing + scaling for the ddb import queue (`app.queue.ddb.*`). IMPORT — one channel per pod; concurrency (K)
 * sizes the dedicated import HTTP pool + broker prefetch. Consumed by [DdbReceiver], sized in [DdbImportConfig].
 */
@Component
@ConfigurationProperties("app.queue.ddb")
class DdbQueueProperties : ImportQueueProperties()
