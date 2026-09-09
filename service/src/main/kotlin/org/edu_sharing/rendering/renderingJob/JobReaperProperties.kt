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

    /**
     * Fallback max time a sub-job (or a main job with no sub-jobs yet) may sit QUEUED before being
     * reaped as orphaned — e.g. a publish that never reached the broker, or a transient queue wiped by
     * a broker/node restart. Deliberately much larger than [defaultMaxProcessTime]: unlike PROCESSING,
     * QUEUED is also the state of a *legitimate* backlog (e.g. HPA scale-up lag under a burst), so this
     * must clear any realistic backlog-drain time before firing, or it reaps jobs that were never lost.
     *
     * NOT the same "legitimate long backlog" reasoning as [maxProcessTime]'s `av_job`: sodix/omega/ddb
     * resolve one link/reference per job via 1-2 REST calls (no download/import), so even a large backlog
     * drains in minutes at their K=50-per-pod concurrency — no override needed for them today.
     */
    var defaultMaxQueuedTime: Duration = Duration.ofHours(6)

    /** Per-routing-key overrides for [defaultMaxQueuedTime] — escape hatch for a future queue whose
     * legitimate backlog-drain time exceeds the default; none needed today. */
    var maxQueuedTime: MutableMap<String, Duration> = mutableMapOf()

    fun maxQueuedTimeFor(routingKey: String): Duration =
        maxQueuedTime[routingKey] ?: defaultMaxQueuedTime

    /** Coarse DB pre-filter counterpart of [minMaxProcessTime], for the QUEUED reaping pass. */
    val minMaxQueuedTime: Duration
        get() = (maxQueuedTime.values + defaultMaxQueuedTime).minOrNull() ?: defaultMaxQueuedTime
}
