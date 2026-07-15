package org.edu_sharing.rendering.modules.sodix

import org.edu_sharing.rendering.core.annotation.ConditionalOnConverter
import org.edu_sharing.rendering.renderingJob.queue.TunableQueueContainerConfig
import org.springframework.stereotype.Component

/**
 * Module-local listener-container tuning for the Sodix queue — a lightweight API passthrough
 * (one blocking HTTP call per message). Prefetch plus this queue's own burst auto-scaling profile,
 * both taken from [SodixQueueProperties] (see [TunableQueueContainerConfig]).
 */
@Component
@ConditionalOnConverter
class SodixQueueConfig(
    properties: SodixQueueProperties,
) : TunableQueueContainerConfig(properties.name, properties.prefetch, properties.scaling)
