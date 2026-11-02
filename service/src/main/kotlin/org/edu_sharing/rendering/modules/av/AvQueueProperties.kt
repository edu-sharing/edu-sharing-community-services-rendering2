package org.edu_sharing.rendering.modules.av

import org.edu_sharing.rendering.renderingJob.queue.StandardQueueProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

/**
 * Routing + scaling for the audio/video queue (`app.queue.av.*`). Consumed by [AvReceiver].
 *
 * The queue carries no `x-max-priority`: quorum queues reject that argument (RabbitMQ 4.3: `invalid arg 'x-max-priority'`)
 * and order by the message `priority` property over the full range 0-31 instead, see `VideoConverterConfig.priorities`.
 */
@Component
@ConfigurationProperties("app.queue.av")
class AvQueueProperties : StandardQueueProperties()
