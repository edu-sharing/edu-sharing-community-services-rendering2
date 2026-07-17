package org.edu_sharing.rendering.renderingJob.entity

enum class SubJobStatus {
    // TIMEOUT is ordered AFTER the non-terminal states so ordinal comparisons like
    // `status <= PROCESSING` (see MainJobLogic) still treat it as terminal. It marks a sub-job the
    // stale-job reaper timed out because processing never completed (e.g. a converter pod crashed).
    QUEUED, PROCESSING, FINISHED, FAILED, TIMEOUT;

    /** A sub-job that ended without a usable result — it either failed outright or was timed out. */
    val isUnsuccessful: Boolean
        get() = this == FAILED || this == TIMEOUT

    companion object {
        fun fromRenderingJobStatus(status: RenderingJobStatus): SubJobStatus = when (status) {
            RenderingJobStatus.QUEUED -> QUEUED
            RenderingJobStatus.PROCESSING -> PROCESSING
            RenderingJobStatus.FINISHED -> FINISHED
            RenderingJobStatus.FAILED -> FAILED
            RenderingJobStatus.PARTIALLY_FAILED -> FAILED
        }
    }
}