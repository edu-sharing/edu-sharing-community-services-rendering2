package org.edu_sharing.rendering.modules.h5p

import org.edu_sharing.rendering.renderingJob.queue.StandardQueueProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

/**
 * Routing for the h5p **lookup** queue (`app.queue.h5p-lookup.*`), consumed by [H5pLookupReceiver].
 *
 * STANDARD — a lookup is one read-only content-id query against lumi, so it fans out freely. Only a miss is
 * handed on to the serialized import queue ([H5pQueueProperties]); keeping the two stages apart is what stops
 * concurrent renders of already-imported packages from queueing up behind a single import.
 */
@Component
@ConfigurationProperties("app.queue.h5p-lookup")
class H5pLookupQueueProperties : StandardQueueProperties()
