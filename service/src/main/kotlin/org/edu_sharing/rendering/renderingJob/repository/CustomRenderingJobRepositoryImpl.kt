package org.edu_sharing.rendering.renderingJob.repository

import org.bson.types.ObjectId
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.RenderingJobStatus
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Repository

@Repository
class CustomRenderingJobRepositoryImpl(
    private val mongoTemplate: MongoTemplate
): CustomRenderingJobRepository {

    private val log = LoggerFactory.getLogger(CustomRenderingJobRepositoryImpl::class.java)

    override fun updateStatusWithoutVersion(
        jobId: ObjectId,
        status: RenderingJobStatus
    ) {
        log.debug("Updating RenderingJob $jobId status to $status (without version check)")
        val query = Query(Criteria.where("_id").`is`(jobId))
        val update = Update()
        update.set("status", status.toString())
        if (status >= RenderingJobStatus.FINISHED) {
            update.set("finishedTimestamp", System.currentTimeMillis())
        }
        val collection = mongoTemplate.getCollectionName(RenderingJob::class.java)
        val updateResult = mongoTemplate.updateFirst(query, update, collection)
        log.info("acknowledged: ${updateResult.wasAcknowledged()} matched: ${updateResult.matchedCount} updated: ${updateResult.modifiedCount}")
    }
}