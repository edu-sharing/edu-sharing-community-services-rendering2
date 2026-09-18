package org.edu_sharing.rendering.modules.h5p.lumi

import org.edu_sharing.rendering.cacheCleaner.TrackingEntry
import org.edu_sharing.rendering.storage.StorageInfo
import org.edu_sharing.rendering.storage.StorageManager
import org.edu_sharing.rendering.storage.StorageScopeKind
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

    /**
     * The per-package H5P library cache as its own quota scope, so the CacheCleaner breathes on it
     * exactly as it does on a bucket: over the upper threshold it deletes least-recently-accessed
     * H5P content down to the lower one.
     *
     * Deleting such an object removes its content, its libraries **and** lumi's node mapping, so the
     * next request for it re-imports — which is what makes the library cache safe to free at all.
     * Its size comes from lumi (the real filesystem fill level), while the candidates are sized by
     * [org.edu_sharing.rendering.cacheCleaner.TrackingEntry.librarySize].
     *
     * Reported only while lumi actually runs the per-package cache and has a quota configured; as in
     * [getManagedBucketQuotas], an unreachable lumi is logged rather than propagated — this runs in
     * the daily cleaner and must not abort it for other repos.
     */
    override fun getAdditionalStorageInfo(repoId: String): List<StorageInfo> {
        return try {
            val info = lumiContentManagementService.getContentBucketInfo(repoId)
            val libraryCache = info.libraryCache ?: return emptyList()
            val quota = libraryCache.quota ?: 0
            if (quota <= 0) return emptyList()
            listOf(
                StorageInfo(
                    repoId = repoId,
                    bucket = info.contentBucket,
                    size = libraryCache.usedBytes ?: 0,
                    maxSize = quota,
                    kind = StorageScopeKind.LIBRARY_CACHE
                )
            )
        } catch (exception: Exception) {
            log.warn("Could not retrieve lumi library cache info for repoId=$repoId: ${exception.message}")
            emptyList()
        }
    }
}
