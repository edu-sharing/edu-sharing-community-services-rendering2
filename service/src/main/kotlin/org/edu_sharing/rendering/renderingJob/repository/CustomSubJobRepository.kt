package org.edu_sharing.rendering.renderingJob.repository

import org.bson.types.ObjectId
import org.edu_sharing.rendering.renderingJob.entity.SubJobStatus

interface CustomSubJobRepository {
    fun updateStatusWithoutVersion(subJobId: ObjectId, status: SubJobStatus)
}
