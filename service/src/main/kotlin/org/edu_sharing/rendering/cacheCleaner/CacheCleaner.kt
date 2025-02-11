package org.edu_sharing.rendering.cacheCleaner

import org.edu_sharing.rendering.core.annotation.ConditionalOnMaster
import org.edu_sharing.rendering.storage.StorageManagerRegistry
import org.edu_sharing.rendering.storage.StorageService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnMaster
class CacheCleaner (
    private val storageService: StorageService,
    private val  storageManagerRegistry: StorageManagerRegistry,
    @Value("\${app.cache.cleaner.threshold.lower}") private val lowerThreshold: Float,
    @Value("\${app.cache.cleaner.threshold.upper}") private val upperThreshold: Float
){
    private val log = LoggerFactory.getLogger(this::class.java)

    @Scheduled(fixedRateString = "\${app.cache.cleaner.schedule}")
    fun cleanCache() {
        log.info("Running cache cleaner...")
        storageService.getStorageInfo().forEach loop@{
            if (it.maxSize == 0L) {
                log.info("No quota set for bucket ${it.location}. Nothing to clean.")
                return@loop
            }
            val usedSpace = it.size.toDouble() / it.maxSize.toDouble()
            log.info( "${it.location}: ${bytesToHumanReadableSize(it.size)} of ${bytesToHumanReadableSize(it.maxSize)} (${(usedSpace * 100).toLong()}%)")

            if(usedSpace > upperThreshold) {

                val bucketManagement = storageManagerRegistry.getBucketManagerByBucketName(it.location)
                if (bucketManagement == null) {
                    log.warn("Unmanaged storage for ${it.location}")
                    return@loop
                }

                try {
                    log.info("Clean cache for ${it.location}")
                    bucketManagement.freeStorage(it, lowerThreshold)
                } catch (e : Exception) {
                    log.error("Error while cleaning cache for ${it.location}", e)
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
