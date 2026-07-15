package org.edu_sharing.rendering.modules.ddb

import org.edu_sharing.rendering.renderingJob.queue.TunableQueueContainerConfig
import org.springframework.stereotype.Component

/**
 * Module-local listener-container tuning for the DDB queue — a lightweight API passthrough
 * (only blocking WebClient calls per message). Not role-gated, mirroring [DdbReceiver]. Prefetch
 * plus this queue's own burst auto-scaling profile, both taken from [DdbQueueProperties] (see
 * [TunableQueueContainerConfig]).
 */
@Component
class DdbQueueConfig(
    properties: DdbQueueProperties,
) : TunableQueueContainerConfig(properties.name, properties.prefetch, properties.scaling)
