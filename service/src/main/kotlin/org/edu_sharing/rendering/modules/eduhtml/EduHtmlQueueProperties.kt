package org.edu_sharing.rendering.modules.eduhtml

import org.edu_sharing.rendering.renderingJob.queue.StandardQueueProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

/**
 * Routing + scaling for the eduHtml queue (`app.queue.edu-html.*`). Consumed by [EduHtmlReceiver]. The prefix is
 * kebab-case because Spring Boot rejects camelCase `@ConfigurationProperties` prefixes.
 */
@Component
@ConfigurationProperties("app.queue.edu-html")
class EduHtmlQueueProperties : StandardQueueProperties()
