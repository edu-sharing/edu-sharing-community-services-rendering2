package org.edu_sharing.rendering.renderingJob

import org.edu_sharing.rendering.core.annotation.ConditionalOnMaster
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * Typed config for the [StaleJobReaper], bound from `app.jobreaper.*`. Master-only, matching the reaper.
 *
 * The reaper decides a PROCESSING sub-job is orphaned once it has been idle longer than its **max process
 * time**. That time is per job type ([maxProcessTime], keyed by the sub-job's `routingKey`, i.e. the queue
 * key such as `av_job`/`document_job`/`sodix_job`), falling back to [defaultMaxProcessTime]. A type's value
 * must exceed the longest *real* processing time for that type, or legitimately slow jobs get reaped.
 */
@Component
@ConditionalOnMaster
@ConfigurationProperties("app.jobreaper")
class JobReaperProperties {
    /** Fallback max process time for any routing key without an explicit [maxProcessTime] entry. */
    var defaultMaxProcessTime: Duration = Duration.ofMinutes(30)

    /** Per-routing-key overrides (e.g. `av_job` runs longer than the default). */
    var maxProcessTime: MutableMap<String, Duration> = mutableMapOf()

    fun maxProcessTimeFor(routingKey: String): Duration =
        maxProcessTime[routingKey] ?: defaultMaxProcessTime

    /**
     * The shortest configured max process time — used as a coarse DB pre-filter so the reaper only pulls
     * sub-jobs that could possibly be stale under *any* type's threshold, then refines per routing key.
     */
    val minMaxProcessTime: Duration
        get() = (maxProcessTime.values + defaultMaxProcessTime).minOrNull() ?: defaultMaxProcessTime
}
