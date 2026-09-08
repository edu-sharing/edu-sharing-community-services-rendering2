package org.edu_sharing.rendering.storage

/**
 * A quota scope for the `CacheCleaner`: either exactly one bucket ([bucket] != null — reported by a
 * [StorageManager] with its own quota, see [StorageManagerRegistry.getManagedBucketQuotas]) or the
 * whole repo across all tracked buckets ([bucket] == null). The latter only applies as long as no
 * single bucket quota is known for this repo (migration path for repos not yet switched to
 * per-bucket quotas).
 */
data class StorageInfo(
    val repoId: String,
    val bucket: String?,
    val size: Long,
    val maxSize: Long
)
