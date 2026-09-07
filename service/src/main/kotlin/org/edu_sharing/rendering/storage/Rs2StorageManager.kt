package org.edu_sharing.rendering.storage

import org.edu_sharing.rendering.cacheCleaner.TrackingEntry
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.edusharingRepo.services.RepositoryRegistrationStorageService
import org.edu_sharing.rendering.storage.bucket.BucketStrategy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class Rs2StorageManager(
    private val storageService: StorageService,
    private val mapper: Mapper,
    @Suppress("SpringJavaInjectionPointsAutowiringInspection")
    private val bucketStrategy: BucketStrategy,
    private val repositoryRegistrationStorageService: RepositoryRegistrationStorageService,
): StorageManager {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun deleteObjectsFromStorage(trackingEntries: List<TrackingEntry>) {
        log.debug("Deleting ${trackingEntries.size} objects from storage via strategy=${bucketStrategy::class.simpleName}")
        val cacheObjects = trackingEntries.map { mapper.trackingEntryToCacheObject(it) }
        storageService.removeObjects(cacheObjects)
    }

    override fun getManagedBuckets(repoId: String): List<String> {
        val buckets = storageService.getBuckets().filter { bucketStrategy.isManagedBucket(it, repoId) }
        log.debug("Managed buckets for repoId=$repoId: $buckets")
        return buckets
    }

    override fun isBucketOwner(bucketName: String, repoId: String): Boolean {
        return bucketStrategy.isManagedBucket(bucketName, repoId)
    }

    /**
     * Only the `renderingBucket` gets an enforced quota — it comes from the registration (decision:
     * the operator knows the contractually agreed capacity of third-party-managed buckets, it can't
     * be read uniformly off the bucket itself). The `tempBucket` deliberately has no [StorageManager]
     * and therefore never shows up here.
     */
    override fun getManagedBucketQuotas(repoId: String): Map<String, Long> {
        val bucket = repositoryRegistrationStorageService.getRegistrationByRepoId(repoId)
            .orElse(null)?.buckets?.renderingBucket ?: return emptyMap()
        return if (bucket.isConfigured && bucket.quota > 0) mapOf(bucket.name to bucket.quota) else emptyMap()
    }
}
