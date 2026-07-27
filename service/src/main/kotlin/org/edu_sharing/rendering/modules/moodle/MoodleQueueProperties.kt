package org.edu_sharing.rendering.modules.moodle

import org.edu_sharing.rendering.renderingJob.queue.StandardQueueProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

/** Routing + scaling for the moodle queue (`app.queue.moodle.*`). Consumed by [MoodleReceiver]. */
@Component
@ConfigurationProperties("app.queue.moodle")
class MoodleQueueProperties : StandardQueueProperties()
