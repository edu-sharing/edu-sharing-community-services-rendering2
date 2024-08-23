package org.edu_sharing.rendering.entity

import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.IndexDirection
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.util.*

@Document("Tracking")
data class TrackingEntry(
    @Id
    var id: ObjectId = ObjectId(),
    val size: Long,
    @Indexed
    val storagePath: String,
    @Indexed
    val bucket: String,
    @Indexed(direction = IndexDirection.ASCENDING)
    var lastAccessed: Date = Date(),
){
    companion object {
        fun of(trackingEntry: TrackingEntry): TrackingEntry{
            return trackingEntry.copy(lastAccessed = Date())
        }
    }
}
