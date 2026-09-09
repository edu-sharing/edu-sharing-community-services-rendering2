package org.edu_sharing.rendering.renderingJob.queue

import org.edu_sharing.rendering.core.annotation.ConditionalOnMaster
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * Typed config for the [LegacyQueueCleaner], bound from `app.queue-migration.*`. Master-only, matching
 * the cleaner.
 *
 * [legacyQueueNames] are the pre-durability-migration queue names (e.g. `image_job_queue`, before the
 * rename to `image_job_queue_v2`) that the cleaner watches for and deletes once orphaned. This list is
 * tied to exactly this migration's before/after state, not a general-purpose tunable — it is not exposed
 * via Helm/Compose (unlike [enabled]/[schedule]), just like the current queue names themselves aren't.
 */
@Component
@ConditionalOnMaster
@ConfigurationProperties("app.queue-migration")
class LegacyQueueCleanupProperties {
    /** Master switch; disable once the migration is confirmed complete and this component is no longer needed. */
    var enabled: Boolean = true

    /** Fixed delay between cleanup runs. */
    var schedule: Duration = Duration.ofMinutes(30)

    /** Pre-migration queue names to watch for and delete once no consumer is attached to them anymore. */
    var legacyQueueNames: MutableList<String> = mutableListOf()
}
