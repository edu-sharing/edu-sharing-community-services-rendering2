package org.edu_sharing.rendering.modules.h5p.lumi

import org.edu_sharing.rendering.cacheCleaner.TrackingEntry
import org.edu_sharing.rendering.storage.StorageManager
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class LumiStorageManager(
    private val lumiContentManagementService: LumiContentManagementService,
): StorageManager {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun deleteObjectsFromStorage(trackingEntries: List<TrackingEntry>) {
        for (trackingEntry in trackingEntries) {
            lumiContentManagementService.deleteContent(trackingEntry)
        }
    }

    override fun getManagedBuckets(repoId: String): List<String> {
        return listOf(lumiContentManagementService.getContentBucket(repoId))
    }

    override fun isBucketOwner(
        bucketName: String,
        repoId: String
    ): Boolean {
        return bucketName == lumiContentManagementService.getContentBucket(repoId);
    }

    /**
     * The quota comes directly from lumi (name and quota are maintained there in the same place,
     * see `GET /edusharing/buckets`), not from rendering2's own repository registration. Unlike
     * [deleteObjectsFromStorage]/[getManagedBuckets], an unreachable lumi is **not** propagated here:
     * this call runs both in the daily CacheCleaner and on every admin dashboard poll — a lumi outage
     * must not abort the cleaner run for other repos, nor the storage display for all repos. Without
     * a known quota the content bucket simply stays untouched/unlisted for this call.
     */
    override fun getManagedBucketQuotas(repoId: String): Map<String, Long> {
        return try {
            val info = lumiContentManagementService.getContentBucketInfo(repoId)
            val quota = info.contentBucketQuota ?: 0
            if (quota > 0) mapOf(info.contentBucket to quota) else emptyMap()
        } catch (exception: Exception) {
            log.warn("Could not retrieve lumi content bucket quota for repoId=$repoId: ${exception.message}")
            emptyMap()
        }
    }
}
