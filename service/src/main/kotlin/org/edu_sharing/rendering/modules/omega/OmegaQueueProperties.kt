package org.edu_sharing.rendering.modules.omega

import org.edu_sharing.rendering.renderingJob.queue.RemoteQueueProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

/**
 * Routing + scaling for the omega queue (`app.queue.omega.*`). REMOTE — one channel per pod; concurrency
 * (K) sizes the dedicated remote HTTP pool + broker prefetch. Consumed by [OmegaReceiver], sized in [OmegaRemoteConfig].
 */
@Component
@ConfigurationProperties("app.queue.omega")
class OmegaQueueProperties : RemoteQueueProperties()
