package org.edu_sharing.rendering.modules.h5p

import org.edu_sharing.rendering.renderingJob.queue.SingleActiveQueueProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

/**
 * Routing for the h5p queue (`app.queue.h5p.*`). SINGLE_ACTIVE — lumi imports one file at a time; the
 * `x-single-active-consumer` argument is declared in [H5pReceiver] and concurrency is forced to 1.
 */
@Component
@ConfigurationProperties("app.queue.h5p")
class H5pQueueProperties : SingleActiveQueueProperties()
