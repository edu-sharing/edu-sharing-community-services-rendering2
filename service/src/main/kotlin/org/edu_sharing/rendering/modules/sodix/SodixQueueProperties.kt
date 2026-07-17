package org.edu_sharing.rendering.modules.sodix

import org.edu_sharing.rendering.renderingJob.queue.RemoteQueueProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

/**
 * Routing + scaling for the sodix queue (`app.queue.sodix.*`). REMOTE — one channel per pod; concurrency
 * (K) sizes the dedicated remote HTTP pool + broker prefetch. Consumed by [SodixReceiver], sized in [SodixRemoteConfig].
 */
@Component
@ConfigurationProperties("app.queue.sodix")
class SodixQueueProperties : RemoteQueueProperties()
