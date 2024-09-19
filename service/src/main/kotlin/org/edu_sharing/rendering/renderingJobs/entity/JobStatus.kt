package org.edu_sharing.rendering.renderingJobs.entity

/**
 * enum class JobStatus
 *
 * A job and its sub jobs can be in the following status:
 *
 * - QUEUED: The job was created and put into the appropriate queue
 * - PROCESSING: The job was received by the queue consumer and is being processed
 * - FINISHED: The job was finished successfully
 * - FAILED: The job failed due to an error
 *
 */
enum class JobStatus {
    QUEUED, PROCESSING, FINISHED, FAILED
}