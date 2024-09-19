package org.edu_sharing.rendering.renderingJob.repository

import org.bson.types.ObjectId
import org.edu_sharing.rendering.renderingJob.entity.JobStatus
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface SubJobRepository: MongoRepository<SubJob, ObjectId> {
    fun countByIdBeforeAndStatusAndRoutingKey(id: ObjectId, status: JobStatus, routingKey: String): Long
}