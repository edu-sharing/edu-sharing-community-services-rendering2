package org.edu_sharing.rendering.renderingJob.repository

import org.bson.types.ObjectId
import org.edu_sharing.rendering.renderingJob.entity.JobStatus

interface CustomRenderingJobRepository {
    fun updateStatusWithoutVersion(jobId: ObjectId, status: JobStatus)
}