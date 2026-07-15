package org.edu_sharing.rendering.modules.omega

import org.edu_sharing.rendering.core.annotation.ConditionalOnConverter
import org.edu_sharing.rendering.renderingJob.queue.TunableQueueContainerConfig
import org.springframework.stereotype.Component

/**
 * Module-local listener-container tuning for the Omega queue — a lightweight API passthrough
 * (one blocking HTTP call per message). Prefetch plus this queue's own burst auto-scaling profile,
 * both taken from [OmegaQueueProperties] (see [TunableQueueContainerConfig]).
 */
@Component
@ConditionalOnConverter
class OmegaQueueConfig(
    properties: OmegaQueueProperties,
) : TunableQueueContainerConfig(properties.name, properties.prefetch, properties.scaling)
