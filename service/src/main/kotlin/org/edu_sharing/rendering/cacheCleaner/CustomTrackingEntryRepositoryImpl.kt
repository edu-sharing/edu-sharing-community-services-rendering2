package org.edu_sharing.rendering.cacheCleaner

import org.bson.Document
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation.count
import org.springframework.data.mongodb.core.aggregation.Aggregation.group
import org.springframework.data.mongodb.core.aggregation.Aggregation.limit
import org.springframework.data.mongodb.core.aggregation.Aggregation.match
import org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation
import org.springframework.data.mongodb.core.aggregation.Aggregation.skip
import org.springframework.data.mongodb.core.aggregation.Aggregation.sort
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.stereotype.Repository
import java.util.Date

@Repository
class CustomTrackingEntryRepositoryImpl(
    private val mongoTemplate: MongoTemplate
) : CustomTrackingEntryRepository {

    companion object {
        private const val COLLECTION = "Tracking"
    }

    override fun aggregateNodes(repoId: String, type: String?, page: Int, size: Int): NodeAggregationResult {
        val criteria = Criteria.where("repoId").`is`(repoId)
        if (type != null) {
            criteria.and("type").`is`(type)
        }

        // Innerhalb jeder nodeId greift `first` nach der vorherigen Sortierung (neueste zuerst).
        val groupStage = group("nodeId")
            .first("type").`as`("type")
            .first("bucket").`as`("bucket")
            .first("hash").`as`("hash")
            .first("binarySize").`as`("size")
            .first("lastAccessed").`as`("lastAccessed")
            .count().`as`("versionCount")
            .sum("binarySize").`as`("totalSize")

        val dataAgg = newAggregation(
            match(criteria),
            sort(Sort.Direction.DESC, "lastAccessed"),
            groupStage,
            sort(Sort.Direction.DESC, "lastAccessed"),
            skip(page.toLong() * size),
            limit(size.toLong())
        )
        val docs = mongoTemplate.aggregate(dataAgg, COLLECTION, Document::class.java).mappedResults

        val countAgg = newAggregation(
            match(criteria),
            group("nodeId"),
            count().`as`("total")
        )
        val total = mongoTemplate.aggregate(countAgg, COLLECTION, Document::class.java)
            .uniqueMappedResult?.getInteger("total")?.toLong() ?: 0L

        val content = docs.map {
            NodeAggregation(
                nodeId = it.getString("_id") ?: "",
                type = it.getString("type") ?: "",
                bucket = it.getString("bucket") ?: "",
                hash = it.getString("hash") ?: "",
                size = (it.get("size") as? Number)?.toLong() ?: 0L,
                lastAccessed = (it.get("lastAccessed") as? Date)?.time ?: 0L,
                versionCount = (it.get("versionCount") as? Number)?.toLong() ?: 0L,
                totalSize = (it.get("totalSize") as? Number)?.toLong() ?: 0L,
            )
        }
        return NodeAggregationResult(content, total)
    }
}
