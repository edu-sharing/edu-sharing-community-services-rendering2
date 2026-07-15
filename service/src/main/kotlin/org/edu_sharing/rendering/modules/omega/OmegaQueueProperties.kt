package org.edu_sharing.rendering.modules.omega

import org.edu_sharing.rendering.core.annotation.ConditionalOnConverter
import org.edu_sharing.rendering.renderingJob.queue.QueueProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

/**
 * Omega queue configuration, bound from `app.queue.omega.*` (name, prefetch, scaling). The
 * sibling `app.queue.omega.{key,concurrency}` keys are consumed only by the [OmegaReceiver]
 * `@RabbitListener` placeholders and are not modelled here (see [QueueProperties]).
 */
@Component
@ConditionalOnConverter
@ConfigurationProperties("app.queue.omega")
class OmegaQueueProperties : QueueProperties()
