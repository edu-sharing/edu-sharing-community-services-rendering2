package org.edu_sharing.rendering.renderingJob.repository

import org.bson.types.ObjectId
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.RenderingJobStatus
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Repository
import java.util.regex.Pattern

@Repository
class CustomRenderingJobRepositoryImpl(
    private val mongoTemplate: MongoTemplate
): CustomRenderingJobRepository {

    private val log = LoggerFactory.getLogger(CustomRenderingJobRepositoryImpl::class.java)

    override fun findJobsPage(
        repoId: String,
        statuses: List<RenderingJobStatus>?,
        search: String?,
        createdFrom: Long?,
        createdTo: Long?,
        pageable: Pageable
    ): Page<RenderingJob> {
        val criteria = Criteria.where("repoId").`is`(repoId)
        if (!statuses.isNullOrEmpty()) {
            criteria.and("status").`in`(statuses)
        }
        val terms = searchTerms(search)
        if (terms.isNotEmpty()) {
            // Jeder Begriff darf auf irgendeinem der Felder matchen (nodeId1, nodeId2 -> ODER).
            criteria.orOperator(
                *terms.flatMap { term ->
                    val q = Pattern.quote(term)
                    listOf(
                        Criteria.where("module").regex(q, "i"),
                        Criteria.where("esObjectId").regex(q, "i"),
                        Criteria.where("errorMessage").regex(q, "i"),
                        Criteria.where("status").regex(q, "i"),
                    )
                }.toTypedArray()
            )
        }
        if (createdFrom != null || createdTo != null) {
            // Einzelne Criteria für creationTimestamp bauen (gte/lte), nicht zweimal .and(<selber key>) –
            // das würde an den Limitierungen des BSON-Dokuments scheitern.
            val ts = Criteria.where("creationTimestamp")
            if (createdFrom != null) ts.gte(createdFrom)
            if (createdTo != null) ts.lte(createdTo)
            criteria.andOperator(ts)
        }
        val total = mongoTemplate.count(Query(criteria), RenderingJob::class.java)
        val content = mongoTemplate.find(Query(criteria).with(pageable), RenderingJob::class.java)
        return PageImpl(content, pageable, total)
    }

    override fun updateStatusWithoutVersion(
        jobId: ObjectId,
        status: RenderingJobStatus
    ) {
        log.debug("Updating RenderingJob $jobId status to $status (without version check)")
        val query = Query(Criteria.where("_id").`is`(jobId))
        val update = Update()
        update.set("status", status.toString())
        if (status == RenderingJobStatus.PROCESSING) {
            update.set("processingStartedTimestamp", System.currentTimeMillis())
        }
        if (status >= RenderingJobStatus.FINISHED) {
            update.set("finishedTimestamp", System.currentTimeMillis())
        }
        val collection = mongoTemplate.getCollectionName(RenderingJob::class.java)
        val updateResult = mongoTemplate.updateFirst(query, update, collection)
        log.debug("acknowledged: ${updateResult.wasAcknowledged()} matched: ${updateResult.matchedCount} updated: ${updateResult.modifiedCount}")
    }

    /** Zerlegt die Sucheingabe an Kommas/Whitespace in einzelne Begriffe (leere verworfen). */
    private fun searchTerms(search: String?): List<String> =
        search?.split(Regex("[,\\s]+"))?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
}