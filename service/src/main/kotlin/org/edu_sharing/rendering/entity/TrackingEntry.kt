package org.edu_sharing.rendering.entity

import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.index.IndexDirection
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.util.*

@Document("Tracking")
@CompoundIndex(name="bucket_storagePath_idx", def="{'bucket': 1, 'age': -1}")
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
