package org.edu_sharing.rendering.storage

import org.edu_sharing.rendering.cacheCleaner.TrackingEntry
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.storage.bucket.BucketStrategy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class Rs2StorageManager(
    private val storageService: StorageService,
    private val mapper: Mapper,
    @Suppress("SpringJavaInjectionPointsAutowiringInspection")
    private val bucketStrategy: BucketStrategy,
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
        return storageService.getBuckets().any { bucketStrategy.isManagedBucket(it, repoId) }
    }
}
