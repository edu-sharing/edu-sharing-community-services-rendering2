package org.edu_sharing.rendering.cacheCleaner

/**
 * Outcome of one cache cleanup run.
 *
 * @property scopesChecked Quota scopes (buckets / repo / library cache) that were examined.
 * @property scopesCleaned Scopes that exceeded their upper threshold and were cleaned.
 * @property deletedEntries Tracked cache objects deleted across all cleaned scopes.
 * @property freedBytes Bytes freed, measured in the unit of each scope (bucket bytes, or library bytes
 *   for a library-cache scope), so a package counted in both is counted twice.
 */
data class CacheCleanupResult(
    val scopesChecked: Int,
    val scopesCleaned: Int,
    val deletedEntries: Int,
    val freedBytes: Long
)
