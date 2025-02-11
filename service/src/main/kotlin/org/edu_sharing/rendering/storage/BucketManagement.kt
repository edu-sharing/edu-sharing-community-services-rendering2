package org.edu_sharing.rendering.storage

/**
 * Interface for managing storage-related operations for a specific bucket type.
 * Implementations of this interface define how storage is managed for different bucket prefixes.
 */
interface BucketManagement {
    /**
     * Returns the prefix associated with the bucket managed by this implementation.
     * The prefix is used to identify and associate the correct bucket manager.
     *
     * @return The bucket prefix as a {@link String}.
     */
    fun bucketPrefix(): String

    /**
     * Frees storage based on the given storage information and a lower threshold.
     * Implementations should ensure that storage usage does not exceed the defined constraints.
     *
     * @param info The {@link StorageInfo} containing details about the current storage state.
     * @param lowerThreshold The percentage threshold (as a {@code Float}) above which storage should be freed.
     */
    fun freeStorage(info: StorageInfo, lowerThreshold: Float)
}
