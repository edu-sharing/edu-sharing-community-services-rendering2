package org.edu_sharing.rendering.modules.sodix

import org.edu_sharing.rendering.renderingJob.queue.ImportQueueProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

/**
 * Routing + scaling for the sodix import queue (`app.queue.sodix.*`). IMPORT — one channel per pod; concurrency
 * (K) sizes the dedicated import HTTP pool + broker prefetch. Consumed by [SodixReceiver], sized in [SodixImportConfig].
 */
@Component
@ConfigurationProperties("app.queue.sodix")
class SodixQueueProperties : ImportQueueProperties()
