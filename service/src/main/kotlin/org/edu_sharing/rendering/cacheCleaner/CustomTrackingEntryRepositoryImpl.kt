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
import java.util.regex.Pattern

@Repository
class CustomTrackingEntryRepositoryImpl(
    private val mongoTemplate: MongoTemplate
) : CustomTrackingEntryRepository {

    companion object {
        private const val COLLECTION = "Tracking"

        // Whitelist: Frontend-Spaltenschlüssel -> Feldname im gruppierten Node-Dokument
        // (`_id` ist die nodeId nach dem $group). Schützt vor beliebigen Sort-Eingaben.
        private val NODE_SORT_FIELDS = mapOf(
            "nodeId" to "_id",
            "type" to "type",
            "size" to "size",
            "totalSize" to "totalSize",
            "lastAccessed" to "lastAccessed",
            "versionCount" to "versionCount",
        )

        // Whitelist für die Typen-Aggregation (`_id` ist der Typname nach dem $group).
        private val TYPE_SORT_FIELDS = mapOf(
            "type" to "_id",
            "count" to "count",
            "totalSize" to "totalSize",
        )

        private fun direction(dir: String): Sort.Direction =
            if (dir.equals("asc", ignoreCase = true)) Sort.Direction.ASC else Sort.Direction.DESC
    }

    override fun aggregateNodes(
        repoId: String,
        type: String?,
        search: String?,
        sort: String?,
        dir: String,
        page: Int,
        size: Int
    ): NodeAggregationResult {
        val criteria = Criteria.where("repoId").`is`(repoId)
        if (type != null) {
            criteria.and("type").`is`(type)
        }
        if (!search.isNullOrBlank()) {
            val q = Pattern.quote(search)
            criteria.orOperator(
                Criteria.where("nodeId").regex(q, "i"),
                Criteria.where("type").regex(q, "i"),
                Criteria.where("hash").regex(q, "i"),
            )
        }

        val sortField = NODE_SORT_FIELDS[sort] ?: "lastAccessed"
        val sortDir = direction(dir)

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
            sort(sortDir, sortField),
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

    override fun aggregateTypes(
        repoId: String,
        search: String?,
        sort: String?,
        dir: String
    ): List<AssetTypeAggregation> {
        val criteria = Criteria.where("repoId").`is`(repoId)
        if (!search.isNullOrBlank()) {
            criteria.and("type").regex(Pattern.quote(search), "i")
        }

        val sortField = TYPE_SORT_FIELDS[sort] ?: "totalSize"
        val sortDir = direction(dir)

        val agg = newAggregation(
            match(criteria),
            group("type").count().`as`("count").sum("binarySize").`as`("totalSize"),
            sort(sortDir, sortField)
        )
        return mongoTemplate.aggregate(agg, COLLECTION, Document::class.java).mappedResults.map {
            AssetTypeAggregation(
                type = it.getString("_id") ?: "",
                count = (it.get("count") as? Number)?.toLong() ?: 0L,
                totalSize = (it.get("totalSize") as? Number)?.toLong() ?: 0L,
            )
        }
    }
}
