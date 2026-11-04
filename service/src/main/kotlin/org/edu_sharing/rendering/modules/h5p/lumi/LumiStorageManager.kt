package org.edu_sharing.rendering.modules.h5p.lumi

import org.edu_sharing.rendering.cacheCleaner.TrackingEntry
import org.edu_sharing.rendering.edusharingRepo.services.RepositoryRegistrationStorageService
import org.edu_sharing.rendering.modules.h5p.H5pRenderModule
import org.edu_sharing.rendering.storage.StorageInfo
import org.edu_sharing.rendering.storage.StorageManager
import org.edu_sharing.rendering.storage.StorageScopeKind
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * lumi is part of a repository's storage only while that repository has the optional module
 * [H5pRenderModule.MODULE_NAME] activated: without it lumi is never consulted for listing buckets,
 * quotas or storage info, so a deployment without lumi stays quiet.
 *
 * Ordered last in the `StorageManagerRegistry`, so its (remote) owner check only runs for buckets
 * no other manager owns.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
class LumiStorageManager(
    private val lumiContentManagementService: LumiContentManagementService,
    private val repositoryRegistrationStorageService: RepositoryRegistrationStorageService,
): StorageManager {
    private val log = LoggerFactory.getLogger(javaClass)

    private fun isH5pActive(repoId: String): Boolean {
        return repositoryRegistrationStorageService.getRegistrationByRepoId(repoId)
            .map { it.optionalModules.contains(H5pRenderModule.MODULE_NAME) }
            .orElse(false)
    }

    override fun deleteObjectsFromStorage(trackingEntries: List<TrackingEntry>) {
        for (trackingEntry in trackingEntries) {
            lumiContentManagementService.deleteContent(trackingEntry)
        }
    }

    /** Empty while H5P is not active for the repo, see the class documentation. */
    override fun getManagedBuckets(repoId: String): List<String> {
        if (!isH5pActive(repoId)) {
            log.debug("H5P not active for repoId=$repoId, no lumi buckets")
            return emptyList()
        }
        return listOf(lumiContentManagementService.getContentBucket(repoId))
    }

    override fun isBucketOwner(
        bucketName: String,
        repoId: String
    ): Boolean {
        // Deliberately not gated on H5P: an entry in lumi's content bucket must still be routed to lumi
        // after H5P was deactivated for the repo. Answering "not mine" would make the registry drop its
        // tracking while the content stays in lumi (the registry deliberately propagates an unreachable
        // lumi instead, see StorageManagerRegistry.getBucketManagerByBucketName).
        return bucketName == lumiContentManagementService.getContentBucket(repoId);
    }

    /**
     * The quota comes directly from lumi (name and quota are maintained there in the same place,
     * see `GET /edusharing/buckets`), not from rendering2's own repository registration. Unlike
     * [deleteObjectsFromStorage]/[getManagedBuckets], an unreachable lumi is **not** propagated here:
     * this call runs both in the daily CacheCleaner and on every admin dashboard poll — a lumi outage
     * must not abort the cleaner run for other repos, nor the storage display for all repos. Without
     * a known quota the content bucket simply stays untouched/unlisted for this call. Without the
     * H5P module for the repo lumi is not asked at all.
     */
    override fun getManagedBucketQuotas(repoId: String): Map<String, Long> {
        if (!isH5pActive(repoId)) return emptyMap()
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
     * the daily cleaner and must not abort it for other repos. Not asked at all without the H5P
     * module for the repo.
     */
    override fun getAdditionalStorageInfo(repoId: String): List<StorageInfo> {
        if (!isH5pActive(repoId)) return emptyList()
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
