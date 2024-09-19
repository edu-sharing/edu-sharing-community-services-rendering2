package org.edu_sharing.rendering.cacheCleaner

import org.bson.types.ObjectId
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface TrackingEntryRepository: MongoRepository<TrackingEntry, ObjectId> {
    fun findByRepoIdAndNodeIdAndHashAndBucket(repoId: String, nodeId: String, hash: String, bucket: String): Optional<TrackingEntry>
    fun deleteByRepoIdAndNodeIdAndHashAndBucket(repoId: String, nodeId: String, hash: String, bucket: String)
    fun findAllByBucket(bucket: String, pageRequest: Pageable): Page<TrackingEntry>
}
