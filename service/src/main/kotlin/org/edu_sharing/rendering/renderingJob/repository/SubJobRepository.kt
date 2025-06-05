package org.edu_sharing.rendering.renderingJob.repository

import org.bson.types.ObjectId
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.edu_sharing.rendering.renderingJob.entity.SubJobStatus
import org.springframework.data.mongodb.repository.Aggregation
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface SubJobRepository: MongoRepository<SubJob, ObjectId>, CustomSubJobRepository {
    @Aggregation(
        "{ \$match: {status: ?2, routingKey: ?3, \$or: [ {priority: { \$gt: ?1 } }, { \$and: [ { priority: ?1 }, { createdDate: { \$lte: ?0 } } ] } ] } }",
        "{ \$count: 'queuePosition' }"
    )
    fun getQueuePosition(createDate: Date?, priority: Int, status: SubJobStatus, routingKey: String): Long?
}
