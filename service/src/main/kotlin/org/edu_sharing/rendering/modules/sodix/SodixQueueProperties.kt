package org.edu_sharing.rendering.modules.sodix

import org.edu_sharing.rendering.core.annotation.ConditionalOnConverter
import org.edu_sharing.rendering.renderingJob.queue.QueueProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

/**
 * Sodix queue configuration, bound from `app.queue.sodix.*` (name, prefetch, scaling). The
 * sibling `app.queue.sodix.{key,concurrency}` keys are consumed only by the [SodixReceiver]
 * `@RabbitListener` placeholders and are not modelled here (see [QueueProperties]).
 */
@Component
@ConditionalOnConverter
@ConfigurationProperties("app.queue.sodix")
class SodixQueueProperties : QueueProperties()
