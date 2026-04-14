package org.edu_sharing.rendering.storage

import org.springframework.lang.Nullable
import org.springframework.stereotype.Component

/**
 * Registry for managing different {@link BucketManagement} implementations based on bucket prefixes.
 *
 * This class initializes a registry of bucket managers by mapping each manager to its associated bucket prefix.
 * It provides a method to retrieve the appropriate {@link BucketManagement} instance for a given bucket name.
 */
@Component
class StorageManagerRegistry(
    @param:Nullable private val storageManagers: List<StorageManager>,
) {
    fun getStorageManagers() = storageManagers

    /**
     * Retrieves the {@link BucketManagement} instance corresponding to the given bucket name.
     * The bucket manager is determined by extracting the prefix of the bucket name (before the first underscore).
     *
     * @param bucketName The name of the bucket.
     * @return The corresponding {@link BucketManagement} instance, or {@code null} if no manager is found.
     */
    fun getBucketManagerByBucketName(bucketName: String, repoId: String): StorageManager? {
        return storageManagers.firstOrNull { it.isBucketOwner(bucketName, repoId) }
    }
}
