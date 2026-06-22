package org.edu_sharing.rendering.cacheCleaner
import org.bson.types.ObjectId
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.Aggregation
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface TrackingEntryRepository: MongoRepository<TrackingEntry, ObjectId>, CustomTrackingEntryRepository {
    fun findByRepoIdAndNodeIdAndHashAndBucket(repoId: String, nodeId: String, hash: String, bucket: String): Optional<TrackingEntry>
    fun deleteByRepoIdAndNodeIdAndHashAndBucket(repoId: String, nodeId: String, hash: String, bucket: String)
    fun findAllByBucket(bucket: String, pageRequest: Pageable): Page<TrackingEntry>
    fun findAllByRepoId(repoId: String, pageRequest: Pageable): Page<TrackingEntry>
    fun findByRepoIdAndNodeIdAndHash(repoId: String, nodeId: String, hash: String): Optional<TrackingEntry>
    fun findAllByRepoIdAndNodeId(repoId: String, nodeId: String): List<TrackingEntry>

    // Admin-Asset-Übersicht (gescopt auf eine repoId, optional nach Typ gefiltert).
    fun findAllByRepoIdAndType(repoId: String, type: String, pageRequest: Pageable): Page<TrackingEntry>
    fun findAllByRepoIdAndType(repoId: String, type: String): List<TrackingEntry>

    @Aggregation("{\$group: { _id: { repoId: '\$repoId', bucket: '\$bucket' },bucketSize: { \$sum: '\$binarySize' } } }",
        "{\$group: { _id: '\$_id.repoId', totalSize: { \$sum: '\$bucketSize' }, buckets: {\$push: { name: '\$_id.bucket', size: '\$bucketSize' } } } }",
        "{\$project: { _id: 0, repoId: '\$_id', totalSize: 1, buckets: 1 } }"
    )
    fun getBucketAggregation(): List<BucketAggregation>
}
