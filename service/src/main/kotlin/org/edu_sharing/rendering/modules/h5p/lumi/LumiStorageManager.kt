package org.edu_sharing.rendering.modules.h5p.lumi

import org.edu_sharing.rendering.cacheCleaner.TrackingEntry
import org.edu_sharing.rendering.edusharingRepo.services.RepositoryRegistrationStorageService
import org.edu_sharing.rendering.modules.h5p.H5pRenderModule
import org.edu_sharing.rendering.modules.h5p.lumi.dto.LumiBucketInfo
import org.edu_sharing.rendering.storage.ManagedScope
import org.edu_sharing.rendering.storage.StorageInfo
import org.edu_sharing.rendering.storage.StorageManager
import org.edu_sharing.rendering.storage.StorageScopeKind
import org.edu_sharing.rendering.storage.bucket.ExternalBucketScopeValidator
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

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

    /**
     * Empty while H5P is not active for the repo, see the class documentation. The scope is named by
     * [LumiBucketInfo.scopeName] and addressed in S3 by lumi's bucket plus its normalized key prefix.
     */
    override fun getManagedScopes(repoId: String): List<ManagedScope> {
        if (!isH5pActive(repoId)) {
            log.debug("H5P not active for repoId=$repoId, no lumi buckets")
            return emptyList()
        }
        val info = lumiContentManagementService.getContentBucketInfo(repoId)
        warnOnScopeOverlap(repoId, info)
        return listOf(ManagedScope(info.scopeName(), info.contentBucket, info.normalizedContentKeyPrefix()))
    }

    override fun isBucketOwner(
        bucketName: String,
        repoId: String
    ): Boolean {
        // Deliberately not gated on H5P: an entry in lumi's content bucket must still be routed to lumi
        // after H5P was deactivated for the repo. Answering "not mine" would make the registry drop its
        // tracking while the content stays in lumi (the registry deliberately propagates an unreachable
        // lumi instead, see StorageManagerRegistry.getBucketManagerByBucketName).
        return bucketName == lumiContentManagementService.getContentScope(repoId)
    }

    /**
     * The quota comes directly from lumi (name and quota are maintained there in the same place,
     * see `GET /edusharing/buckets`), not from rendering2's own repository registration. Unlike
     * [deleteObjectsFromStorage]/[getManagedScopes], an unreachable lumi is **not** propagated here:
     * this call runs both in the daily CacheCleaner and on every admin dashboard poll — a lumi outage
     * must not abort the cleaner run for other repos, nor the storage display for all repos. Without
     * a known quota the content bucket simply stays untouched/unlisted for this call. Without the
     * H5P module for the repo lumi is not asked at all.
     */
    override fun getManagedBucketQuotas(repoId: String): Map<String, Long> {
        if (!isH5pActive(repoId)) return emptyMap()
        return try {
            val info = lumiContentManagementService.getContentBucketInfo(repoId)
            warnOnScopeOverlap(repoId, info)
            val quota = info.contentBucketQuota ?: 0
            if (quota > 0) mapOf(info.scopeName() to quota) else emptyMap()
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
                    bucket = info.scopeName(),
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

    private val warnedOverlaps: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /**
     * Warns (once per repo and lumi scope) when lumi's content scope overlaps the repo's rendering or
     * temp scope in the same bucket. Dedicated warning for the dangerous case: lumi without a key prefix
     * in the repo's rendering bucket - its scope name then equals the rendering bucket name, so the
     * service's own storage manager claims lumi's H5P entries and lumi's quota replaces the rendering one. Best effort: never throws, the callers deliberately swallow lumi outages.
     */
    private fun warnOnScopeOverlap(repoId: String, info: LumiBucketInfo) {
        try {
            if (!warnedOverlaps.add("$repoId|${info.scopeName()}")) return
            val buckets = repositoryRegistrationStorageService.getRegistrationByRepoId(repoId)
                .orElse(null)?.buckets ?: return
            val lumi = ExternalBucketScopeValidator.Scope(
                "lumi content", info.contentBucket, info.normalizedContentKeyPrefix()
            )
            val others = listOfNotNull(
                buckets.renderingBucket?.let { "renderingBucket" to it },
                buckets.tempBucket?.let { "tempBucket" to it }
            ).filter { (_, bucket) -> bucket.isConfigured }
                .map { (label, bucket) -> ExternalBucketScopeValidator.Scope(label, bucket.name, bucket.normalizedKeyPrefix()) }
            val rendering = buckets.renderingBucket?.takeIf { it.isConfigured }
            if (lumi.prefix.isEmpty() && rendering != null && rendering.name == info.contentBucket) {
                log.warn(
                    "lumi stores its content at the root of the service's rendering bucket '{}' (no " +
                        "CONTENT_AWS_S3_KEY_PREFIX) for repoId={}: the service cannot tell lumi's storage from its own - " +
                        "its storage manager claims lumi's H5P tracking entries (the cache cleaner never frees lumi's " +
                        "content) and lumi's quota replaces the rendering bucket's quota. Set CONTENT_AWS_S3_KEY_PREFIX.",
                    info.contentBucket, repoId
                )
            }
            ExternalBucketScopeValidator.findOverlaps(listOf(lumi) + others)
                .filter { (a, _) -> a === lumi }
                .forEach { (_, other) ->
                    log.warn(
                        "lumi content scope '{}' (bucket '{}', key prefix '{}') of repoId={} overlaps the service's {} " +
                            "scope (key prefix '{}'). Configure distinct, non-nested prefixes: " +
                            "CONTENT_AWS_S3_KEY_PREFIX (lumi) / buckets[].keyPrefix (service).",
                        info.scopeName(), info.contentBucket, info.normalizedContentKeyPrefix(), repoId,
                        other.label, other.prefix
                    )
                }
        } catch (exception: Exception) {
            log.debug("Could not check lumi scope overlap for repoId=$repoId: ${exception.message}")
        }
    }
}
