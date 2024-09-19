package org.edu_sharing.rendering.renderingJobs.repository

import org.bson.types.ObjectId
import org.edu_sharing.rendering.renderingJobs.entity.RenderingJob
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface RenderingJobRepository: MongoRepository<RenderingJob, ObjectId> {
    fun findAllByEsObjectId(id: String): List<RenderingJob>
}