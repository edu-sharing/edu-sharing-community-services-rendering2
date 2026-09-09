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
        if (status == SubJobStatus.PROCESSING) {
            update.set("processingStartedDate", Instant.now())
        }
        if (status.isUnsuccessful || status == SubJobStatus.FINISHED) {
            update.set("finishedDate", Instant.now())
        }
        // Entity-class overload so the WriteConcernResolver maps SubJob -> ACKNOWLEDGED (see timeoutSubJobs);
        // the collection-name overload leaves MongoAction.entityType null -> UNACKNOWLEDGED, on which reading
        // matchedCount/modifiedCount below throws UnsupportedOperationException.
        val updateResult = mongoTemplate.updateFirst(query, update, SubJob::class.java)
        if (updateResult.wasAcknowledged()) {
            log.info("acknowledged: true matched: ${updateResult.matchedCount} updated: ${updateResult.modifiedCount}")
        } else {
            log.info("SubJob $subJobId status update sent (unacknowledged write)")
        }
    }

    override fun findProcessingSubJobsModifiedBefore(cutoff: Instant): List<StaleSubJobView> =
        findByStatusModifiedBefore(SubJobStatus.PROCESSING, cutoff)

    override fun findQueuedSubJobsModifiedBefore(cutoff: Instant): List<StaleSubJobView> =
        findByStatusModifiedBefore(SubJobStatus.QUEUED, cutoff)

    private fun findByStatusModifiedBefore(status: SubJobStatus, cutoff: Instant): List<StaleSubJobView> {
        // status is persisted as the enum name (see updateStatusWithoutVersion); match the string form.
        val query = Query(
            Criteria.where("status").`is`(status.toString())
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
            .set("finishedDate", Instant.now())
        // Use the entity-class overload, NOT the collection-name one: the WriteConcernResolver maps
        // SubJob -> ACKNOWLEDGED via MongoAction.entityType, which the collection-name overload leaves
        // null -> it falls through to UNACKNOWLEDGED, and reading matchedCount/modifiedCount on an
        // unacknowledged result throws. Guard the count read regardless, in case the concern changes.
        val updateResult = mongoTemplate.updateMulti(query, update, SubJob::class.java)
        if (updateResult.wasAcknowledged()) {
            log.info("Timed out stale sub-jobs — matched: ${updateResult.matchedCount} modified: ${updateResult.modifiedCount}")
        } else {
            log.info("Timed out ${subJobIds.size} stale sub-job(s) (unacknowledged write)")
        }
    }
}
