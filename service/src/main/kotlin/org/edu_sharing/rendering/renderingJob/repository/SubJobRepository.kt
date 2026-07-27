package org.edu_sharing.rendering.renderingJob.repository

import org.bson.types.ObjectId
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.edu_sharing.rendering.renderingJob.entity.SubJobStatus
import org.springframework.data.mongodb.repository.Aggregation
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface SubJobRepository: MongoRepository<SubJob, ObjectId>, CustomSubJobRepository {
    @Aggregation(
        "{ \$match: {status: ?2, routingKey: ?3, \$or: [ {priority: { \$gt: ?1 } }, { \$and: [ { priority: ?1 }, { createdDate: { \$lte: ?0 } } ] } ] } }",
        "{ \$count: 'queuePosition' }"
    )
    fun getQueuePosition(createDate: Date?, priority: Int, status: SubJobStatus, routingKey: String): Long?

    // Die @DocumentReference 'parent' speichert die ObjectId des RenderingJob direkt im Feld.
    // Über diesen Wert lassen sich SubJobs eines Jobs finden bzw. (kaskadierend) löschen –
    // die DocumentReference löscht NICHT von selbst kaskadierend.
    @Query("{ 'parent': ?0 }")
    fun findByParentId(parentId: ObjectId): List<SubJob>

    @Query(value = "{ 'parent': ?0 }", delete = true)
    fun deleteByParentId(parentId: ObjectId)

    // Sub-Job-Status-Kennzahlen pro Repo (Dashboard). Die SubJobs referenzieren den
    // RenderingJob über 'parent'; die repoId hängt am RenderingJob -> $lookup auf die
    // RenderingJob-Collection und Match auf repoId.
    @Aggregation(
        "{ \$lookup: { from: 'renderingJob', localField: 'parent', foreignField: '_id', as: 'job' } }",
        "{ \$unwind: '\$job' }",
        "{ \$match: { 'job.repoId': ?0 } }",
        "{ \$group: { _id: '\$status', count: { \$sum: 1 } } }",
        "{ \$project: { _id: 0, status: '\$_id', count: 1 } }"
    )
    fun countSubJobsByStatusForRepo(repoId: String): List<SubJobStatusCount>
}

data class SubJobStatusCount(
    val status: SubJobStatus,
    val count: Long
)
