package org.edu_sharing.rendering.renderingJob.repository

import org.bson.types.ObjectId
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.RenderingJobStatus
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface RenderingJobRepository: MongoRepository<RenderingJob, ObjectId>, CustomRenderingJobRepository {
    fun findAllByEsObjectId(id: String): List<RenderingJob>
    fun findByIdAndStatus(id: ObjectId, status: RenderingJobStatus): RenderingJob?
}
