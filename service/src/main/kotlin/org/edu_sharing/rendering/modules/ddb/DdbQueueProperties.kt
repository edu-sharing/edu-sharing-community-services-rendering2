package org.edu_sharing.rendering.modules.ddb

import org.edu_sharing.rendering.renderingJob.queue.RemoteQueueProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

/**
 * Routing + scaling for the ddb queue (`app.queue.ddb.*`). REMOTE — one channel per pod; concurrency (K)
 * sizes the dedicated remote HTTP pool + broker prefetch. Consumed by [DdbReceiver], sized in [DdbRemoteConfig].
 */
@Component
@ConfigurationProperties("app.queue.ddb")
class DdbQueueProperties : RemoteQueueProperties()
