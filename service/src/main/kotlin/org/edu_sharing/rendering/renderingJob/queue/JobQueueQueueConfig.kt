package org.edu_sharing.rendering.renderingJob.queue

import org.edu_sharing.rendering.core.annotation.ConditionalOnConverter
import org.edu_sharing.rendering.modules.sodix.SodixQueueProperties
import org.edu_sharing.rendering.renderingJob.queue.TunableQueueContainerConfig
import org.springframework.stereotype.Component

/**
 * Module-local listener-container tuning for the Job queue — a lightweight Job Handler and Scheduler.
 * Prefetch plus this queue's own burst auto-scaling profile,
 * both taken from [JobQueueProperties] (see [TunableQueueContainerConfig]).
 */
@Component
@ConditionalOnConverter
class JobQueueQueueConfig(
    properties: JobQueueProperties,
) : TunableQueueContainerConfig(properties.name, properties.prefetch, properties.scaling)
