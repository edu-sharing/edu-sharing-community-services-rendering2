package org.edu_sharing.rendering.modules.moodle

import org.edu_sharing.rendering.renderingJob.queue.SingleActiveQueueProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

/**
 * Routing for the moodle queue (`app.queue.moodle.*`). SINGLE_ACTIVE — moodle imports one file at a time; the
 * `x-single-active-consumer` argument is declared in [MoodleReceiver] and concurrency is forced to 1.
 */
@Component
@ConfigurationProperties("app.queue.moodle")
class MoodleQueueProperties : SingleActiveQueueProperties()
