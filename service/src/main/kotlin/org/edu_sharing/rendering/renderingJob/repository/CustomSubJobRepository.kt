package org.edu_sharing.rendering.renderingJob.repository

import org.bson.types.ObjectId
import org.edu_sharing.rendering.renderingJob.entity.SubJobStatus
import java.time.Instant

interface CustomSubJobRepository {
    fun updateStatusWithoutVersion(subJobId: ObjectId, status: SubJobStatus)

    /**
     * Lightweight projection of sub-jobs still in PROCESSING whose last activity ([lastModifiedDate]) is
     * older than [cutoff] — the candidate set for the stale-job reaper. Deliberately a projection (not the
     * full [org.edu_sharing.rendering.renderingJob.entity.SubJob]) so the lazy `parent` document reference
     * is read as its raw [ObjectId] instead of triggering a per-row lookup.
     */
    fun findProcessingSubJobsModifiedBefore(cutoff: Instant): List<StaleSubJobView>

    /**
     * Same projection as [findProcessingSubJobsModifiedBefore], but for sub-jobs still in QUEUED — the
     * candidate set for the reaper's QUEUED safety-net pass (a publish that never reached the broker, or
     * a queue that no longer exists, e.g. deleted by
     * [org.edu_sharing.rendering.renderingJob.queue.LegacyQueueCleaner] — never redelivers).
     */
    fun findQueuedSubJobsModifiedBefore(cutoff: Instant): List<StaleSubJobView>

    /**
     * Bulk versionless transition of the given sub-jobs to [SubJobStatus.TIMEOUT] with [errorMessage].
     * Bypasses optimistic locking (like [updateStatusWithoutVersion]) since the reaper races nothing —
     * the owning consumer is gone. No-op on an empty collection.
     */
    fun timeoutSubJobs(subJobIds: Collection<ObjectId>, errorMessage: String)

    /**
     * Bumps [lastModifiedDate] to now, without touching [status] or `@Version` — used by
     * [org.edu_sharing.rendering.renderingJob.SubJobHeartbeat] to keep a legitimately long-running
     * PROCESSING sub-job from looking idle to the stale-job reaper. Bypasses optimistic locking
     * deliberately: a heartbeat racing the real completion write is harmless (both just set a field to a
     * slightly different value), and failing it would defeat the point.
     */
    fun touchLastModifiedDate(subJobId: ObjectId)
}

/** Minimal view of a stale sub-job for the reaper; `parent` is the owning RenderingJob's id. */
data class StaleSubJobView(
    val id: ObjectId,
    val routingKey: String,
    val lastModifiedDate: Instant?,
    val parent: ObjectId,
)
