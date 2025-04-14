package org.edu_sharing.rendering.renderingJob.entity

enum class SubJobStatus {
    QUEUED, PROCESSING, FINISHED, FAILED;

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