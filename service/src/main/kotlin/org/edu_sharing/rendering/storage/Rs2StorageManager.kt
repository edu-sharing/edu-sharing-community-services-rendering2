package org.edu_sharing.rendering.storage

import org.edu_sharing.rendering.cacheCleaner.TrackingEntry
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.storage.bucket.BucketStrategy
import org.springframework.stereotype.Component

@Component
class Rs2StorageManager(
    private val storageService: StorageService,
    private val mapper: Mapper,
    @Suppress("SpringJavaInjectionPointsAutowiringInspection")
    private val bucketStrategy: BucketStrategy,
): StorageManager {

    override fun deleteObjectsFromStorage(trackingEntries: List<TrackingEntry>) {
        val cacheObjects = trackingEntries.map { mapper.trackingEntryToCacheObject(it) }
        storageService.removeObjects(cacheObjects)
    }

    override fun getManagedBuckets(repoId: String): List<String> {
        return storageService.getBuckets().filter { bucketStrategy.isManagedBucket(it, repoId) }
    }

    override fun isBucketOwner(bucketName: String, repoId: String): Boolean {
        return storageService.getBuckets().any { bucketStrategy.isManagedBucket(it, repoId) }
    }
}
