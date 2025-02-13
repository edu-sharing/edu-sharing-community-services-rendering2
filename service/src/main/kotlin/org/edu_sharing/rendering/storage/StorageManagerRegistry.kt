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
class StorageManagerRegistry(@Nullable private val bucketManagers: List<BucketManagement>) {

    private final val registeredManager = bucketManagers.associateBy { it.bucketPrefix() }

    /**
     * Retrieves the {@link BucketManagement} instance corresponding to the given bucket name.
     * The bucket manager is determined by extracting the prefix of the bucket name (before the first underscore).
     *
     * @param bucketName The name of the bucket.
     * @return The corresponding {@link BucketManagement} instance, or {@code null} if no manager is found.
     */
    fun getBucketManagerByBucketName(bucketName:  String): BucketManagement? = registeredManager[bucketName.substringBefore("-")]
}
