package org.edu_sharing.rendering.integration

import org.bson.Document
import org.edu_sharing.rendering.edusharingRepo.entity.RepositoryRegistration
import org.edu_sharing.rendering.edusharingRepo.services.ExternalBucketMigrationRunner
import org.edu_sharing.rendering.edusharingRepo.services.RepositoryRegistrationStorageService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.data.mongodb.core.MongoTemplate

/**
 * Verifies backward compatibility for `RepositoryRegistration` documents whose
 * `buckets.renderingBucket`/`tempBucket` still hold a plain string (from before the switch to
 * `ExternalBucket` with a quota): both the reading converter (`MongoConfig.StringToExternalBucketConverter`)
 * and the one-time migration runner (`ExternalBucketMigrationRunner`).
 *
 * The runner normally runs at application startup (`ApplicationRunner`) — which happens before this
 * test's document is inserted, so it is invoked directly here instead of relying on the Spring
 * lifecycle timing.
 */
class ExternalBucketMigrationRunnerTest(
    @param:Autowired private val mongoTemplate: MongoTemplate,
    @param:Autowired private val migrationRunner: ExternalBucketMigrationRunner,
    @param:Autowired private val registrationStorageService: RepositoryRegistrationStorageService,
) : AbstractIntegrationTest() {

    private fun collection() = mongoTemplate.getCollection(mongoTemplate.getCollectionName(RepositoryRegistration::class.java))

    private fun insertLegacyDocument(repoId: String) {
        collection().insertOne(
            Document()
                .append("repoId", repoId)
                .append("url", "https://$repoId.example.org/edu-sharing")
                .append("publicKey", "key")
                .append("quota", 0L)
                .append(
                    "buckets",
                    Document("renderingBucket", "legacy-rendering-bucket").append("tempBucket", "legacy-temp-bucket")
                )
        )
    }

    @Test
    fun `reading converter treats a legacy string bucket as ExternalBucket without quota`() {
        val repoId = "legacy-bucket-read"
        insertLegacyDocument(repoId)

        val registration = registrationStorageService.getRegistrationByRepoId(repoId).orElseThrow()

        assertEquals("legacy-rendering-bucket", registration.buckets?.renderingBucket?.name)
        assertEquals(0L, registration.buckets?.renderingBucket?.quota)
        assertEquals("legacy-temp-bucket", registration.buckets?.tempBucket?.name)
        assertEquals(0L, registration.buckets?.tempBucket?.quota)
    }

    @Test
    fun `migration runner rewrites legacy string buckets to ExternalBucket documents`() {
        val repoId = "legacy-bucket-migrate"
        insertLegacyDocument(repoId)

        migrationRunner.run(DefaultApplicationArguments())

        val migrated = collection().find(Document("repoId", repoId)).first()
        val buckets = migrated?.get("buckets", Document::class.java)
        assertTrue(buckets?.get("renderingBucket") is Document, "renderingBucket should now be a sub-document")
        assertEquals("legacy-rendering-bucket", buckets?.get("renderingBucket", Document::class.java)?.getString("name"))
        assertEquals(0L, buckets?.get("renderingBucket", Document::class.java)?.getLong("quota"))
        assertTrue(buckets?.get("tempBucket") is Document, "tempBucket should now be a sub-document")

        // idempotent: a second run must not fail and must leave the already-migrated document alone
        migrationRunner.run(DefaultApplicationArguments())
        val stillMigrated = collection().find(Document("repoId", repoId)).first()
        assertEquals(migrated, stillMigrated)
    }

    @Test
    fun `migration runner leaves already-migrated documents untouched`() {
        val repoId = "already-migrated"
        registrationStorageService.storeRegistration(
            RepositoryRegistration(
                repoId = repoId,
                url = "https://$repoId.example.org/edu-sharing",
                publicKey = "key",
                buckets = org.edu_sharing.rendering.edusharingRepo.entity.ExternalBuckets(
                    renderingBucket = org.edu_sharing.rendering.edusharingRepo.entity.ExternalBucket(name = "rb", quota = 42),
                ),
            )
        )

        migrationRunner.run(DefaultApplicationArguments())

        val registration = registrationStorageService.getRegistrationByRepoId(repoId).orElseThrow()
        assertEquals("rb", registration.buckets?.renderingBucket?.name)
        assertEquals(42L, registration.buckets?.renderingBucket?.quota)
    }
}
