package org.edu_sharing.rendering.storage

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Registry for managing different {@link BucketManagement} implementations based on bucket prefixes.
 *
 * This class initializes a registry of bucket managers by mapping each manager to its associated bucket prefix.
 * It provides a method to retrieve the appropriate {@link BucketManagement} instance for a given bucket name.
 */
@Component
class StorageManagerRegistry(
    private val storageManagers: List<StorageManager> = emptyList(),
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun getStorageManagers() = storageManagers

    /**
     * Retrieves the {@link BucketManagement} instance corresponding to the given bucket name.
     * The bucket manager is determined by extracting the prefix of the bucket name (before the first underscore).
     *
     * @param bucketName The name of the bucket.
     * @return The corresponding {@link BucketManagement} instance, or {@code null} if no manager is found.
     */
    fun getBucketManagerByBucketName(bucketName: String, repoId: String): StorageManager? {
        // An owner check may need a remote call (lumi); a failing manager must not hide a healthy one that
        // owns the bucket. Only if nobody claims the bucket is the failure propagated, so an entry is never
        // mistaken for "unowned" (and its tracking dropped) merely because its owner was unreachable.
        var failure: Exception? = null
        val manager = storageManagers.firstOrNull {
            try {
                it.isBucketOwner(bucketName, repoId)
            } catch (exception: Exception) {
                log.warn("Owner check of ${it.javaClass.simpleName} failed for bucket=$bucketName, repoId=$repoId: ${exception.message}")
                failure = failure ?: exception
                false
            }
        }
        if (manager == null) failure?.let { throw it }
        log.debug("Resolved bucket manager for bucket=$bucketName, repoId=$repoId: ${manager?.javaClass?.simpleName ?: "none"}")
        return manager
    }

    /**
     * Bucket name → quota to enforce, merged across all registered [StorageManager]s for this repo
     * (see [StorageManager.getManagedBucketQuotas]). Used by the `CacheCleaner` (which buckets get
     * their own scope) and by the admin storage endpoint (display per bucket).
     */
    fun getManagedBucketQuotas(repoId: String): Map<String, Long> {
        return storageManagers
            .flatMap { it.getManagedBucketQuotas(repoId).entries }
            .associate { it.key to it.value }
    }
}
