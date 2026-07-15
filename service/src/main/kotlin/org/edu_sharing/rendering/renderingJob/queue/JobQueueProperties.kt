package org.edu_sharing.rendering.renderingJob.queue

import org.edu_sharing.rendering.core.annotation.ConditionalOnConverter
import org.edu_sharing.rendering.renderingJob.queue.QueueProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

/**
 * Job queue configuration, bound from `app.queue.job.*` (name, prefetch, scaling). The
 * sibling `app.queue.job.{key,concurrency}` keys are consumed only by the [JobReceiver]
 * `@RabbitListener` placeholders and are not modelled here (see [QueueProperties]).
 */
@Component
@ConditionalOnConverter
@ConfigurationProperties("app.queue.job")
class JobQueueProperties : QueueProperties()
