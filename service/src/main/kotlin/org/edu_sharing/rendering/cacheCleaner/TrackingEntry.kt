package org.edu_sharing.rendering.cacheCleaner

import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import java.util.*

@Document("Tracking")
@CompoundIndexes(
    // Covers:
    // 1. findByRepoIdAndNodeIdAndHashAndBucket
    // 2. findByRepoIdAndNodeIdAndHash
    // 3. findAllByRepoIdAndNodeId
    // 4. findAllByRepoId
    CompoundIndex(name="repo_node_hash_bucket_idx", def="{'repoId': 1, 'nodeId': 1, 'hash': 1, 'bucket': 1}", unique = true),
    
    // Optimized for CacheCleaner:
    // Covers: findAllByRepoId sorted by lastAccessed
    CompoundIndex(name="repo_lastAccessed_idx", def="{'repoId': 1, 'lastAccessed': 1}"),

    // Covers: findAllByBucket
    // Also covers queries filtering by bucket and lastAccessed
    CompoundIndex(name="bucket_lastAccessed_idx", def="{'bucket': 1, 'lastAccessed': 1}"),

    // Trägt GET /admin/assets/nodes (AdminAssetController.listAssetNodes ->
    // CustomTrackingEntryRepositoryImpl.aggregateNodes) für den Fall mit Typ-Filter: 'type'
    // (bzw. jetzt 'types' via $in) direkt nach 'repoId', damit der $match der Aggregation per
    // Index bedient wird statt jeden Tracking-Eintrag des Repos in-memory nach Typ zu filtern;
    // 'lastAccessed' danach liefert die anschließende $sort-Stage (die vor dem $group über die
    // Versionen steht, um pro nodeId die neueste Version zu bestimmen) direkt aus dem Index statt
    // per In-Memory-Sort. Ohne Typ-Filter greift weiterhin repo_lastAccessed_idx.
    CompoundIndex(name="repo_type_lastAccessed_idx", def="{'repoId': 1, 'type': 1, 'lastAccessed': -1}")
)
data class TrackingEntry(
    @Id
    val id: ObjectId = ObjectId(),
    val repoId: String,
    val nodeId: String,
    val hash: String,
    val type: String,
    val bucket: String,
    var binarySize: Long,
    var lastAccessed: Date = Date(),

    ){
    companion object {
        fun of(trackingEntry: TrackingEntry): TrackingEntry{
            return trackingEntry.copy(lastAccessed = Date())
        }

        fun of(repoId: String, nodeId: String, hash: String, type: String, bucket: String, binarySize: Long): TrackingEntry{
            return TrackingEntry(
                repoId = repoId,
                nodeId = nodeId,
                hash = hash,
                bucket = bucket,
                type = type,
                binarySize = binarySize,
                lastAccessed = Date()
            )
        }
    }
}
