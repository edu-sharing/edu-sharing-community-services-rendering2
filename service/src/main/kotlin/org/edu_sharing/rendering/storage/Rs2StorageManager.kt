package org.edu_sharing.rendering.storage

import org.edu_sharing.rendering.cacheCleaner.TrackingEntry
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.edusharingRepo.services.RepositoryRegistrationStorageService
import org.edu_sharing.rendering.storage.bucket.BucketStrategy
import org.springframework.stereotype.Component

@Component
class Rs2StorageManager(
    private val storageService: StorageService,
    private val mapper: Mapper,
    @Suppress("SpringJavaInjectionPointsAutowiringInspection")
    private val bucketStrategy: BucketStrategy,
    private val repositoryRegistrationStorageService: RepositoryRegistrationStorageService,
): StorageManager {

    private val prefix = "rs2"
    override fun deleteObjectsFromStorage(trackingEntries: List<TrackingEntry>) {
        val cacheObjects = trackingEntries.map { mapper.trackingEntryToCacheObject(it) }
        storageService.removeObjects(cacheObjects)
    }

    override fun getManagedBuckets(repoId: String): List<String> {
        if (bucketStrategy.isPrefixBased()) return storageService.getBuckets().filter { it.startsWith(prefix) }
        val registration = repositoryRegistrationStorageService.getRegistrationByRepoId(repoId)
        return listOf(registration.get().buckets?.renderingBucket ?: "")
    }

    override fun getByBucketName(bucketName: String, repoId: String): StorageManager? {
        if (bucketStrategy.isPrefixBased()) {
            return if (bucketName.startsWith(prefix)) this else null
        }
        val registration = repositoryRegistrationStorageService.getRegistrationByRepoId(repoId)
        return if (bucketName == registration.get().buckets?.renderingBucket) this else null
    }
}
