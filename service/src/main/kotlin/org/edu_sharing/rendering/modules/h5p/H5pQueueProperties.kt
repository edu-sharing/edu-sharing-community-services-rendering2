package org.edu_sharing.rendering.modules.h5p

import org.edu_sharing.rendering.renderingJob.queue.SingleActiveQueueProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

/**
 * Routing for the h5p **import** queue (`app.queue.h5p.*`), consumed by [H5pImportReceiver].
 *
 * SINGLE_ACTIVE — lumi imports one file at a time; the `x-single-active-consumer` argument is declared in
 * [H5pImportReceiver] and concurrency is forced to 1. Only sub-jobs that missed the lookup stage
 * ([H5pLookupQueueProperties]) reach this queue, so the serialization costs nothing for content lumi already
 * holds.
 */
@Component
@ConfigurationProperties("app.queue.h5p")
class H5pQueueProperties : SingleActiveQueueProperties()
