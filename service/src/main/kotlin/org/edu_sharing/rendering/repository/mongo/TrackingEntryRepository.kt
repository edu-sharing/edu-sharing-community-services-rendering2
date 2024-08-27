package org.edu_sharing.rendering.repository.mongo

import org.bson.types.ObjectId
import org.edu_sharing.rendering.entity.TrackingEntry
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface TrackingEntryRepository: MongoRepository<TrackingEntry, ObjectId> {
    fun findByRepoIdAndNodeIdAndHashAndBucket(repoId: String, nodeId: String, hash: String, bucket: String): Optional<TrackingEntry>
    fun deleteByRepoIdAndNodeIdAndHashAndBucket(repoId: String, nodeId: String, hash: String, bucket: String)
    fun findAllByBucket(bucket: String, pageRequest: PageRequest): Page<TrackingEntry>
//    fun findByBucketAndStoragePath(bucket: String, storagePath: String): Optional<TrackingEntry>
//    fun deleteByBucketAndStoragePath(bucket: String, storagePath: String)
}
