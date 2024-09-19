package org.edu_sharing.rendering.cacheCleaner

import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.util.*

@Document("Tracking")
@CompoundIndexes(
    CompoundIndex(name="bucket_repo_id_node_id_hash_idx", def="{'repoId': 1, 'node': 1, 'hash': 1, 'bucket': 1}", unique = true),
    CompoundIndex(name="bucket_access_idx", def="{'bucket': 1, 'lastAccessed': 1}")
)
data class TrackingEntry(
    @Id
    val id: ObjectId = ObjectId(),

    // StoragePath
    val repoId: String,
    val nodeId: String,
    val hash: String,
    val type: String,
    val bucket: String,

    @Indexed
    var lastAccessed: Date = Date(),

    ){
    companion object {
        fun of(trackingEntry: TrackingEntry): TrackingEntry{
            return trackingEntry.copy(lastAccessed = Date())
        }

        fun of(repoId: String, nodeId: String, hash: String, type: String, bucket: String): TrackingEntry{
            return TrackingEntry(
                repoId = repoId,
                nodeId = nodeId,
                hash = hash,
                bucket = bucket,
                type = type,
                lastAccessed = Date()
            )
        }
    }
}
