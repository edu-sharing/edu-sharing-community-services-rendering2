package org.edu_sharing.rendering.cacheCleaner

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.edu_sharing.rendering.core.annotation.ConditionalOnMaster
import org.edu_sharing.rendering.storage.StorageManagerRegistry
import org.edu_sharing.rendering.storage.StorageScopeKind
import org.edu_sharing.rendering.storage.StorageService
import org.edu_sharing.rendering.utils.takeUntil
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnMaster
class CacheCleaner(
    private val storageService: StorageService,
    private val storageManagerRegistry: StorageManagerRegistry,
    @param:Value($$"${app.cache.cleaner.threshold.lower}") private val lowerThreshold: Float,
    @param:Value($$"${app.cache.cleaner.threshold.upper}") private val upperThreshold: Float,
    private val trackingService: TrackingService,
    private val quotaMetrics: StorageQuotaMetrics
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    @Scheduled(
        cron = $$"${app.cache.cleaner.schedule}"
    )
    @SchedulerLock(name = "cacheCleaner", lockAtMostFor = "30m", lockAtLeastFor = "1m")
    fun cleanCache() {
        cleanCache(repoId = null)
    }

    /**
     * Runs one cleanup pass, optionally restricted to the scopes of a single [repoId]. Unlike the
     * scheduled [cleanCache] this is not guarded by the ShedLock, so a manual trigger is never
     * silently skipped because a scheduled run happened a moment ago.
     */
    fun cleanCache(repoId: String?): CacheCleanupResult {
        log.info("Running cache cleaner${repoId?.let { " for repo $it" } ?: ""}...")
        var scopesChecked = 0
        var scopesCleaned = 0
        var deletedEntries = 0
        var freedBytes = 0L
        val scopes = try {
            storageService.getStorageInfo().filter { repoId == null || it.repoId == repoId }
        } catch (exception: Exception) {
            log.error("Could not determine storage info, skipping cache cleaner run: ${exception.message}")
            quotaMetrics.failure(StorageScopeKind.BUCKET)
            return CacheCleanupResult(0, 0, 0, 0L)
        }
        scopes.forEach loop@{ scope ->
            try {
            val label = when {
                scope.kind == StorageScopeKind.LIBRARY_CACHE -> "${scope.repoId}/${scope.bucket} (H5P library cache)"
                scope.bucket != null -> "${scope.repoId}/${scope.bucket}"
                else -> scope.repoId
            }
            quotaMetrics.record(scope, scope.size, upperThreshold)
            if (scope.maxSize == 0L) {
                log.info("No quota set for $label. Nothing to clean.")
                return@loop
            }
            scopesChecked++
            val usedSpace = scope.size.toDouble() / scope.maxSize.toDouble()
            log.info("$label: ${bytesToHumanReadableSize(scope.size)} of ${bytesToHumanReadableSize(scope.maxSize)} (${(usedSpace * 100).toLong()}%)")
            // Quotas are soft - nothing refuses a request over them - so the log is where an operator hears about it
            // (and the rendering.storage.* metrics, see StorageQuotaMetrics). Above the quota is the alarm; above the
            // upper threshold is only the cleaner doing its job.
            if (usedSpace > 1.0) {
                log.warn(
                    "Quota exceeded for $label: ${bytesToHumanReadableSize(scope.size)} of " +
                        "${bytesToHumanReadableSize(scope.maxSize)} (${(usedSpace * 100).toLong()}%)"
                )
            }

            log.debug("Threshold check for $label: usedRatio=${String.format("%.4f", usedSpace)}, upperThreshold=$upperThreshold, lowerThreshold=$lowerThreshold")
            if (usedSpace > upperThreshold) {
                scopesCleaned++
                val targetSize = (lowerThreshold * scope.maxSize).toLong()
                val bytesToFree = scope.size - targetSize
                log.debug("Cleanup triggered for $label: target size=${bytesToHumanReadableSize(targetSize)}, freeing ~${bytesToHumanReadableSize(bytesToFree)}")
                val trackingIterator = if (scope.bucket != null) {
                    trackingService.getTrackedObjectsByRepoIdAndBucket(scope.repoId, scope.bucket)
                } else {
                    trackingService.getTrackedObjectsByRepoId(scope.repoId)
                }

                val bucketEntryGroups = trackingIterator.asSequence()
                    // Objects that occupy nothing in the resource being freed would be deleted for
                    // no gain - notably H5P content imported before the per-package library cache,
                    // which has no library set of its own.
                    .filter { scope.kind.sizeOf(it) > 0 }
                    .takeUntil(0L, { freed, _ -> freed >= bytesToFree }) { freed, element ->
                        freed + scope.kind.sizeOf(element)
                    }
                    .groupBy { entry -> storageManagerRegistry.getBucketManagerByBucketName(entry.bucket, entry.repoId) }

                var scopeFreed = 0L
                log.debug("Deletion candidates for $label: ${bucketEntryGroups.values.sumOf { e -> e.size }} entries across ${bucketEntryGroups.size} bucket manager(s)")
                bucketEntryGroups.forEach { (bucketManager, entries) ->
                    log.debug("Bulk-deleting ${entries.size} entries via ${bucketManager?.javaClass?.simpleName ?: "no manager"}")
                    try {
                        bucketManager?.deleteObjectsFromStorage(entries)
                        trackingService.deleteAllTrackedObjects(entries)
                        val bytes = entries.sumOf { scope.kind.sizeOf(it) }
                        deletedEntries += entries.size
                        freedBytes += bytes
                        scopeFreed += bytes
                        quotaMetrics.cleaned(scope.kind, entries.size, bytes)
                    } catch (exception: Exception) {
                        quotaMetrics.failure(scope.kind)
                        // Keep the tracking entries so the next run retries them, and carry on with
                        // the remaining buckets/scopes instead of aborting the whole pass.
                        log.warn("Cleanup of ${entries.size} entries failed for $label, will retry on the next run: ${exception.message}")
                    }
                }
                val remaining = (scope.size - scopeFreed).coerceAtLeast(0)
                // The sizes are only collected once per run, so this is the best estimate until the next one.
                quotaMetrics.record(scope, remaining, upperThreshold)
                if (scopeFreed < bytesToFree) {
                    log.warn(
                        "Cleanup of $label freed only ${bytesToHumanReadableSize(scopeFreed)} of the " +
                            "${bytesToHumanReadableSize(bytesToFree)} needed to get below ${(lowerThreshold * 100).toInt()}% - " +
                            "about ${bytesToHumanReadableSize(remaining)} of ${bytesToHumanReadableSize(scope.maxSize)} " +
                            "(${(remaining * 100 / scope.maxSize)}%) remain" +
                            if (remaining > scope.maxSize) ", still over the quota" else ""
                    )
                }
            }
            } catch (exception: Exception) {
                // One failing scope must not keep the others from being cleaned.
                quotaMetrics.failure(scope.kind)
                log.error("Cache cleaner failed for ${scope.repoId}/${scope.bucket}: ${exception.message}")
            }
        }
        // Scopes that are gone (a deleted repo, ...) must not keep reporting their last size - but a run for a
        // single repo only saw that repo's scopes.
        if (repoId == null) {
            quotaMetrics.retainOnly(scopes)
        }
        quotaMetrics.runFinished()
        return CacheCleanupResult(scopesChecked, scopesCleaned, deletedEntries, freedBytes)
    }

    private fun bytesToHumanReadableSize(bytes: Long) = when {
        bytes >= 1 shl 30 -> "%.1f GB".format(bytes.toDouble() / (1 shl 30))
        bytes >= 1 shl 20 -> "%.1f MB".format(bytes.toDouble() / (1 shl 20))
        bytes >= 1 shl 10 -> "%.0f kB".format(bytes.toDouble() / (1 shl 10))
        else -> "$bytes bytes"
    }
}
