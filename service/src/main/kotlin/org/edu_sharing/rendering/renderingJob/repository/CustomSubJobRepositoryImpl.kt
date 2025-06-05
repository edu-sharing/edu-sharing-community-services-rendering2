package org.edu_sharing.rendering.renderingJob.repository

import org.bson.types.ObjectId
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.edu_sharing.rendering.renderingJob.entity.SubJobStatus
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Repository

@Repository
class CustomSubJobRepositoryImpl(
    private val mongoTemplate: MongoTemplate
): CustomSubJobRepository {

    private val log = LoggerFactory.getLogger(this::class.java)

    override fun updateStatusWithoutVersion(
        subJobId: ObjectId,
        status: SubJobStatus
    ) {
        val query = Query(Criteria.where("_id").`is`(subJobId))
        val update = Update()
        update.set("status", status.toString())
        val collection = mongoTemplate.getCollectionName(SubJob::class.java)
        val updateResult = mongoTemplate.updateFirst(query, update, collection)
        log.info("acknowledged: ${updateResult.wasAcknowledged()} matched: ${updateResult.matchedCount} updated: ${updateResult.modifiedCount}")
    }
}
