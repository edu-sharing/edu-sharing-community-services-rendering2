package org.edu_sharing.rendering.cacheCleaner

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
class CacheCleaner (
    private val storageService: StorageService,
    private val storageManagerRegistry: StorageManagerRegistry,
    @param:Value("\${app.cache.cleaner.threshold.lower}") private val lowerThreshold: Float,
    @param:Value("\${app.cache.cleaner.threshold.upper}") private val upperThreshold: Float,
    private val trackingService: TrackingService
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    @Scheduled(
        fixedDelayString = "\${app.cache.cleaner.schedule}",
        initialDelayString = "\${app.cache.cleaner.schedule}"
    )
    fun cleanCache() {
        log.info("Running cache cleaner...")
        storageService.getStorageInfo().forEach loop@{
            if (it.maxSize == 0L) {
                log.info("No quota set for repoId ${it.location}. Nothing to clean.")
                return@loop
            }
            val usedSpace = it.size.toDouble() / it.maxSize.toDouble()
            log.info("${it.location}: ${bytesToHumanReadableSize(it.size)} of ${bytesToHumanReadableSize(it.maxSize)} (${(usedSpace * 100).toLong()}%)")

            if (usedSpace > upperThreshold) {
                val maxSize = (lowerThreshold * it.maxSize).toLong()
                val trackingIterator = trackingService.getTrackedObjectsByRepoId(it.location)

                val bucketEntryGroups = trackingIterator.asSequence()
                    .takeUntil(0L, { totalSize, _ -> totalSize >= maxSize }) { totalSize, element ->
                        totalSize + element.binarySize
                    }
                    .groupBy { entry -> storageManagerRegistry.getBucketManagerByBucketName(entry.bucket) }

                bucketEntryGroups.forEach { (bucketManager, entries) ->
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
