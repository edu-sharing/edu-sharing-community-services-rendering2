package org.edu_sharing.rendering.storage

import org.edu_sharing.rendering.cacheCleaner.TrackingEntry

/**
 * What a quota scope measures, and therefore which size of a [TrackingEntry] counts towards freeing
 * it. The distinction exists because deleting one cached object frees space in two unrelated places:
 * its bucket, and - for an H5P package - lumi's per-package library cache.
 */
enum class StorageScopeKind {
    /** A storage bucket, measured in [TrackingEntry.binarySize]. */
    BUCKET,

    /** Lumi's per-package H5P library cache, measured in [TrackingEntry.librarySize]. */
    LIBRARY_CACHE;

    /** The size of [entry] in the unit this scope is measured in. */
    fun sizeOf(entry: TrackingEntry): Long = when (this) {
        BUCKET -> entry.binarySize
        LIBRARY_CACHE -> entry.librarySize
    }
}

/**
 * A quota scope for the `CacheCleaner`: either exactly one bucket ([bucket] != null — reported by a
 * [StorageManager] with its own quota, see [StorageManagerRegistry.getManagedBucketQuotas]) or the
 * whole repo across all tracked buckets ([bucket] == null). The latter only applies as long as no
 * single bucket quota is known for this repo (migration path for repos not yet switched to
 * per-bucket quotas).
 *
 * A scope with [kind] `LIBRARY_CACHE` is not a bucket of its own: it draws its deletion candidates
 * from [bucket] (the H5P content bucket, whose tracked objects are the packages) but is measured in
 * library bytes. A repo can therefore have two scopes over the same bucket — one per resource — and
 * either of them exceeding its threshold triggers a cleanup, which is the point.
 */
data class StorageInfo(
    val repoId: String,
    val bucket: String?,
    val size: Long,
    val maxSize: Long,
    val kind: StorageScopeKind = StorageScopeKind.BUCKET
)
