package org.edu_sharing.rendering.renderingJob.entity

/**
 * enum class RenderingJobStatus
 *
 * A rendering job can be in the following status:
 *
 * - QUEUED: The job was created and put into the appropriate queue
 * - PROCESSING: The job was received by the queue consumer and is being processed
 * - FINISHED: The job was finished successfully
 * - FAILED: All sub jobs failed
 * - PARTIALLY_FAILED: One or more sub jobs finished successfully and
 *
 */
enum class RenderingJobStatus {
    QUEUED, PROCESSING, FINISHED, FAILED, PARTIALLY_FAILED
}