package org.edu_sharing.rendering.modules.ddb

import org.edu_sharing.rendering.renderingJob.queue.QueueProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

/**
 * DDB queue configuration, bound from `app.queue.ddb.*` (name, prefetch, scaling). Not role-gated,
 * mirroring [DdbReceiver]. The sibling `app.queue.ddb.{key,concurrency}` keys are consumed only by
 * the [DdbReceiver] `@RabbitListener` placeholders and are not modelled here (see [QueueProperties]).
 */
@Component
@ConfigurationProperties("app.queue.ddb")
class DdbQueueProperties : QueueProperties()
