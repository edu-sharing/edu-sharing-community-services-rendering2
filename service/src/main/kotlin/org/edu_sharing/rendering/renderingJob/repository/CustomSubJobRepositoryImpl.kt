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
import java.time.Instant

@Repository
class CustomSubJobRepositoryImpl(
    private val mongoTemplate: MongoTemplate
): CustomSubJobRepository {

    private val log = LoggerFactory.getLogger(this::class.java)

    override fun updateStatusWithoutVersion(
        subJobId: ObjectId,
        status: SubJobStatus
    ) {
        log.debug("Updating SubJob $subJobId status to $status (without version check)")
        val query = Query(Criteria.where("_id").`is`(subJobId))
        val update = Update()
        update.set("status", status.toString())
        val collection = mongoTemplate.getCollectionName(SubJob::class.java)
        val updateResult = mongoTemplate.updateFirst(query, update, collection)
        log.info("acknowledged: ${updateResult.wasAcknowledged()} matched: ${updateResult.matchedCount} updated: ${updateResult.modifiedCount}")
    }

    override fun findProcessingSubJobsModifiedBefore(cutoff: Instant): List<StaleSubJobView> {
        // status is persisted as the enum name (see updateStatusWithoutVersion); match the string form.
        val query = Query(
            Criteria.where("status").`is`(SubJobStatus.PROCESSING.toString())
                .and("lastModifiedDate").lt(cutoff)
        )
        query.fields().include("routingKey", "lastModifiedDate", "parent")
        return mongoTemplate.find(query, StaleSubJobView::class.java, mongoTemplate.getCollectionName(SubJob::class.java))
    }

    override fun timeoutSubJobs(subJobIds: Collection<ObjectId>, errorMessage: String) {
        if (subJobIds.isEmpty()) return
        val query = Query(Criteria.where("_id").`in`(subJobIds))
        val update = Update()
            .set("status", SubJobStatus.TIMEOUT.toString())
            .set("errorMessage", errorMessage)
        val collection = mongoTemplate.getCollectionName(SubJob::class.java)
        val updateResult = mongoTemplate.updateMulti(query, update, collection)
        log.info("Timed out stale sub-jobs — matched: ${updateResult.matchedCount} updated: ${updateResult.modifiedCount}")
    }
}
