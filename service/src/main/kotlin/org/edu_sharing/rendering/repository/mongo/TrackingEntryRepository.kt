package org.edu_sharing.rendering.repository.mongo

import org.bson.types.ObjectId
import org.edu_sharing.rendering.entity.TrackingEntry
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface TrackingEntryRepository: MongoRepository<TrackingEntry, ObjectId> {
    fun findByBucketAndStoragePath(bucket: String, storagePath: String): Optional<TrackingEntry>
    fun deleteByStoragePath(storagePath: String)
}
