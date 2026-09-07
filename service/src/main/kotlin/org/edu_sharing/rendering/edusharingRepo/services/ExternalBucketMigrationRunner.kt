package org.edu_sharing.rendering.edusharingRepo.services

import org.bson.Document
import org.edu_sharing.rendering.core.annotation.ConditionalOnMaster
import org.edu_sharing.rendering.edusharingRepo.entity.RepositoryRegistration
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Component

/**
 * One-time, idempotent cleanup pass for `RepositoryRegistration` documents whose
 * `buckets.renderingBucket`/`tempBucket` still hold a plain string from before the switch to
 * [ExternalBucket] (name + quota). `StringToExternalBucketConverter`
 * ([org.edu_sharing.rendering.config.MongoConfig]) already reads those legacy values correctly
 * (quota 0), but this runner additionally rewrites them into the new shape so an operator can set
 * the quota afterwards through the same properties that already maintain the bucket name.
 *
 * The `$type` filter + `$set` pipeline update is a no-op per call for already-migrated documents —
 * several master replicas running this concurrently at startup is harmless.
 */
@Component
@ConditionalOnMaster
class ExternalBucketMigrationRunner(
    private val mongoTemplate: MongoTemplate
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private val BUCKET_FIELDS = listOf("renderingBucket", "tempBucket")
    }

    override fun run(args: ApplicationArguments) {
        val collection = mongoTemplate.getCollection(mongoTemplate.getCollectionName(RepositoryRegistration::class.java))
        BUCKET_FIELDS.forEach { field ->
            val path = "buckets.$field"
            val filter = Document(path, Document("\$type", "string"))
            val pipeline = listOf(
                Document(
                    "\$set",
                    Document(path, Document("name", "\$$path").append("quota", 0L))
                )
            )
            val result = collection.updateMany(filter, pipeline)
            if (result.modifiedCount > 0) {
                log.info("Migrated legacy string bucket '$field' to ExternalBucket on ${result.modifiedCount} repository registration(s)")
            }
        }
    }
}
