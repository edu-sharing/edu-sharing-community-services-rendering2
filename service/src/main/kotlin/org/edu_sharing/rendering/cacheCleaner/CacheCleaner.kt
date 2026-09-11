package org.edu_sharing.rendering.cacheCleaner

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.edu_sharing.rendering.core.annotation.ConditionalOnMaster
import org.edu_sharing.rendering.storage.StorageManagerRegistry
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
    private val trackingService: TrackingService
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    @Scheduled(
        cron = $$"${app.cache.cleaner.schedule}"
    )
    @SchedulerLock(name = "cacheCleaner", lockAtMostFor = "30m", lockAtLeastFor = "1m")
    fun cleanCache() {
        log.info("Running cache cleaner...")
        storageService.getStorageInfo().forEach loop@{ scope ->
            val label = if (scope.bucket != null) "${scope.repoId}/${scope.bucket}" else scope.repoId
            if (scope.maxSize == 0L) {
                log.info("No quota set for $label. Nothing to clean.")
                return@loop
            }
            val usedSpace = scope.size.toDouble() / scope.maxSize.toDouble()
            log.info("$label: ${bytesToHumanReadableSize(scope.size)} of ${bytesToHumanReadableSize(scope.maxSize)} (${(usedSpace * 100).toLong()}%)")

            log.debug("Threshold check for $label: usedRatio=${String.format("%.4f", usedSpace)}, upperThreshold=$upperThreshold, lowerThreshold=$lowerThreshold")
            if (usedSpace > upperThreshold) {
                val targetSize = (lowerThreshold * scope.maxSize).toLong()
                val bytesToFree = scope.size - targetSize
                log.debug("Cleanup triggered for $label: target size=${bytesToHumanReadableSize(targetSize)}, freeing ~${bytesToHumanReadableSize(bytesToFree)}")
                val trackingIterator = if (scope.bucket != null) {
                    trackingService.getTrackedObjectsByRepoIdAndBucket(scope.repoId, scope.bucket)
                } else {
                    trackingService.getTrackedObjectsByRepoId(scope.repoId)
                }

                val bucketEntryGroups = trackingIterator.asSequence()
                    .takeUntil(0L, { freed, _ -> freed >= bytesToFree }) { freed, element ->
                        freed + element.binarySize
                    }
                    .groupBy { entry -> storageManagerRegistry.getBucketManagerByBucketName(entry.bucket, entry.repoId) }

                log.debug("Deletion candidates for $label: ${bucketEntryGroups.values.sumOf { e -> e.size }} entries across ${bucketEntryGroups.size} bucket manager(s)")
                bucketEntryGroups.forEach { (bucketManager, entries) ->
                    log.debug("Bulk-deleting ${entries.size} entries via ${bucketManager?.javaClass?.simpleName ?: "no manager"}")
                    bucketManager?.deleteObjectsFromStorage(entries)
                    trackingService.deleteAllTrackedObjects(entries)
                }
            }
        }
    }

    private fun bytesToHumanReadableSize(bytes: Long) = when {
        bytes >= 1 shl 30 -> "%.1f GB".format(bytes.toDouble() / (1 shl 30))
        bytes >= 1 shl 20 -> "%.1f MB".format(bytes.toDouble() / (1 shl 20))
        bytes >= 1 shl 10 -> "%.0f kB".format(bytes.toDouble() / (1 shl 10))
        else -> "$bytes bytes"
    }
}
