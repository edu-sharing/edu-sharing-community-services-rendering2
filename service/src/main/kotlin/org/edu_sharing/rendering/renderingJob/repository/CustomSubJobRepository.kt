package org.edu_sharing.rendering.renderingJob.repository

import org.bson.types.ObjectId
import org.edu_sharing.rendering.renderingJob.entity.SubJobStatus
import java.time.Instant

interface CustomSubJobRepository {
    fun updateStatusWithoutVersion(subJobId: ObjectId, status: SubJobStatus)

    /**
     * Atomically claim a sub-job for a link refresh: transition it from a terminal state
     * (FINISHED/FAILED/TIMEOUT) back to QUEUED in a single conditional update, clearing any prior
     * error. Returns `true` iff this call won the claim (matched exactly one terminal sub-job).
     *
     * A `false` return means the sub-job is already QUEUED/PROCESSING — a refresh (or the initial
     * fetch) is already in flight for the owning job — so the caller must NOT re-enqueue. This is
     * the race-free guard that ensures at most one refresh runs per job at a time.
     */
    fun claimForRefresh(subJobId: ObjectId): Boolean

    /**
     * Lightweight projection of sub-jobs still in PROCESSING whose last activity ([lastModifiedDate]) is
     * older than [cutoff] — the candidate set for the stale-job reaper. Deliberately a projection (not the
     * full [org.edu_sharing.rendering.renderingJob.entity.SubJob]) so the lazy `parent` document reference
     * is read as its raw [ObjectId] instead of triggering a per-row lookup.
     */
    fun findProcessingSubJobsModifiedBefore(cutoff: Instant): List<StaleSubJobView>

    /**
     * Bulk versionless transition of the given sub-jobs to [SubJobStatus.TIMEOUT] with [errorMessage].
     * Bypasses optimistic locking (like [updateStatusWithoutVersion]) since the reaper races nothing —
     * the owning consumer is gone. No-op on an empty collection.
     */
    fun timeoutSubJobs(subJobIds: Collection<ObjectId>, errorMessage: String)
}

/** Minimal view of a stale sub-job for the reaper; `parent` is the owning RenderingJob's id. */
data class StaleSubJobView(
    val id: ObjectId,
    val routingKey: String,
    val lastModifiedDate: Instant?,
    val parent: ObjectId,
)
