package org.edu_sharing.rendering.scheduler

import org.edu_sharing.rendering.blobStorage.StorageService
import org.edu_sharing.rendering.config.annotation.ConditionalOnCacheCleaner
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnCacheCleaner
class CacheCleaner (
    private val storageService: StorageService,
    @Value("\${app.cache.cleaner.threshold.lower}") private val lowerThreshold: Float,
    @Value("\${app.cache.cleaner.threshold.upper}") private val upperThreshold: Float
){
    private val log = LoggerFactory.getLogger(this::class.java)

    @Scheduled(fixedRateString = "\${app.cache.cleaner.schedule}")
    fun cleanCache() {
        log.info("Running cache cleaner...")
        storageService.getStorageInfo().forEach{
            val usedSpace = it.size.toDouble() / it.maxSize.toDouble()
            log.info( "${it.location}: ${bytesToHumanReadableSize(it.size)} of ${bytesToHumanReadableSize(it.maxSize)} (${(usedSpace * 100).toLong()}%)")

            if(usedSpace > upperThreshold) {
                log.info("clean cache for ${it.location}")
                storageService.freeStorage(it, lowerThreshold)
            }
        }
    }

    private fun bytesToHumanReadableSize(bytes: Long) = when {
        bytes >= 1 shl 30 -> "%.1f GB".format(bytes / (1 shl 30))
        bytes >= 1 shl 20 -> "%.1f MB".format(bytes / (1 shl 20))
        bytes >= 1 shl 10 -> "%.0f kB".format(bytes / (1 shl 10))
        else -> "$bytes bytes"
    }
}
