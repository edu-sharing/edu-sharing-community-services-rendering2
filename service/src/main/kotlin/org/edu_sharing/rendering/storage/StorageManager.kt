package org.edu_sharing.rendering.storage

import org.edu_sharing.rendering.cacheCleaner.TrackingEntry

/**
 * Interface for managing storage-related operations for a specific bucket type.
 * Implementations of this interface define how storage is managed for different bucket prefixes.
 */
interface StorageManager {
    /**
     * Frees storage based on the given storage information and a lower threshold.
     * Implementations should ensure that storage usage does not exceed the defined constraints.
     *
     * @param trackingEntries the tracking entry/entries to be removed
     */
    fun deleteObjectsFromStorage(trackingEntries: List<TrackingEntry>)

    fun getManagedBuckets(repoId: String): List<String>

    fun getByBucketName(bucketName: String, repoId: String): StorageManager?
}
