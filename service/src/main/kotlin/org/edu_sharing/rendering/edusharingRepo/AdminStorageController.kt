package org.edu_sharing.rendering.edusharingRepo

import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.edu_sharing.rendering.cacheCleaner.TrackingService
import org.edu_sharing.rendering.core.annotation.ConditionalOnMaster
import org.edu_sharing.rendering.core.exception.EntryNotFoundException
import org.edu_sharing.rendering.edusharingRepo.dto.BucketUsageInfo
import org.edu_sharing.rendering.edusharingRepo.dto.StorageUsageInfo
import org.edu_sharing.rendering.edusharingRepo.entity.RepositoryRegistration
import org.edu_sharing.rendering.edusharingRepo.services.RepositoryRegistrationStorageService
import org.edu_sharing.rendering.storage.StorageManagerRegistry
import org.edu_sharing.rendering.storage.StorageService
import org.edu_sharing.rendering.storage.bucket.BucketPerCustomerStrategy
import org.edu_sharing.rendering.storage.bucket.BucketStrategy
import org.edu_sharing.rendering.storage.bucket.ExternalBucketStrategy
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Admin endpoints for the dashboard's storage overview, scoped to exactly one repoId.
 *
 * By default the cheap, repo-accurate tracking aggregations are used
 * ([TrackingService.getBucketAggregation]). Passing `exact=true` together with a specific [bucket]
 * name recomputes just that one bucket's size live from S3 ([StorageService.getDirectorySize]) —
 * every other bucket in the response still comes from tracking. This is only actually done when
 * [isRepoAccurate] holds and the bucket is confirmed ([BucketStrategy.isManagedBucket]) to belong to
 * this repo — otherwise a live recount can't be attributed to one repo (see [isRepoAccurate]) and the
 * request silently falls back to the tracked value for that bucket. The temp bucket is handled the
 * same way (see [tempBucketUsage]): it is only ever live-measured when [bucket] names it explicitly,
 * never on automatic polling — a live listing there is otherwise skipped as too expensive.
 *
 * Bucket quotas come from [StorageManagerRegistry.getManagedBucketQuotas] — the same source as the
 * `CacheCleaner` — except for the temp bucket, which never has an
 * [org.edu_sharing.rendering.storage.StorageManager] and whose quota is therefore read directly from
 * the registration.
 */
@RestController
@RequestMapping("/admin")
@SecurityRequirement(name = "basicAuth")
@Tag(name = "storage")
@ConditionalOnMaster
class AdminStorageController(
    private val trackingService: TrackingService,
    private val storageService: StorageService,
    private val bucketStrategy: BucketStrategy,
    private val repositoryRegistrationStorageService: RepositoryRegistrationStorageService,
    private val storageManagerRegistry: StorageManagerRegistry
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping("/storage/usage")
    fun getStorageUsage(
        @RequestParam repoId: String,
        @RequestParam(required = false, defaultValue = "false") exact: Boolean,
        @RequestParam(required = false) bucket: String? = null
    ): StorageUsageInfo {
        log.debug("GET /admin/storage/usage for repoId=$repoId, exact=$exact, bucket=$bucket")

        val registration = repositoryRegistrationStorageService.getRegistrationByRepoId(repoId)
            .orElseThrow { EntryNotFoundException("No repository registration found for repoId $repoId.") }

        val aggregation = trackingService.getBucketAggregation().firstOrNull { it.repoId == repoId }
        val trackedBuckets = aggregation?.buckets?.map { it.name to it.size } ?: emptyList()

        // Only recompute the one requested bucket, and only when its contents can actually be
        // attributed to this single repo — see isRepoAccurate().
        val exactBucket = (exact && bucket != null && isRepoAccurate() && bucketStrategy.isManagedBucket(bucket, repoId))
            .takeIf { it }
            ?.let { bucket!! to storageService.getDirectorySize(bucket, "") }

        val enforcedQuotas = storageManagerRegistry.getManagedBucketQuotas(repoId)

        val buckets = trackedBuckets.map { (name, trackedSize) ->
            val size = if (name == exactBucket?.first) exactBucket.second else trackedSize
            val bucketQuota = enforcedQuotas[name]
            BucketUsageInfo(
                name = name,
                size = size,
                quota = bucketQuota,
                usedPercent = bucketQuota?.let { (size.toDouble() / it.toDouble()) * 100.0 },
                enforced = bucketQuota != null,
                measured = true
            )
        }.toMutableList()

        tempBucketUsage(registration, exact, bucket)
            ?.takeIf { temp -> buckets.none { it.name == temp.name } }
            ?.let { buckets += it }

        // The repo-wide size/quota deliberately covers only the tracked/managed buckets, not the
        // temp bucket (which is never enforced and whose size is unknown without exact=true — else
        // the overall figure would jump depending on whether "Measure exact" was just clicked).
        val totalSize = trackedBuckets.sumOf { (name, trackedSize) -> if (name == exactBucket?.first) exactBucket.second else trackedSize }
        val quota = registration.quota.takeIf { it > 0 }
        val usedPercent = quota?.let { (totalSize.toDouble() / it.toDouble()) * 100.0 }

        return StorageUsageInfo(
            repoId = repoId,
            totalSize = totalSize,
            quota = quota,
            usedPercent = usedPercent,
            exact = exactBucket != null,
            buckets = buckets
        )
    }

    /**
     * Whether a live S3 recount of a bucket in [bucketStrategy] can be attributed to exactly one
     * repo: true for per-customer buckets (`rs2-<repoId>`, one bucket per repo) and external buckets
     * (each repo owns its own dedicated bucket(s), configured on the registration). False for
     * per-media-type buckets (`rs2-<type>`), which are shared across every repo's objects — an S3
     * listing there can't be narrowed down to one repo's share.
     */
    private fun isRepoAccurate(): Boolean =
        bucketStrategy is BucketPerCustomerStrategy || bucketStrategy is ExternalBucketStrategy

    /**
     * The temp bucket is barely tracked in practice, and a live measurement
     * ([StorageService.getDirectorySize]) is expensive (a full `ListObjectsV2` over the bucket) —
     * hence only when it is itself the explicitly [requestedBucket] of an `exact=true` request (a
     * manual per-bucket trigger in the frontend), never on automatic polling. `null` if the temp
     * bucket has no quota configured (then, as before, it stays hidden entirely).
     */
    private fun tempBucketUsage(
        registration: RepositoryRegistration,
        exact: Boolean,
        requestedBucket: String?
    ): BucketUsageInfo? {
        val tempBucket = registration.buckets?.tempBucket?.takeIf { it.isConfigured && it.quota > 0 } ?: return null
        val measureExact = exact && requestedBucket == tempBucket.name
        val size = if (measureExact) storageService.getDirectorySize(tempBucket.name, "") else 0L
        return BucketUsageInfo(
            name = tempBucket.name,
            size = size,
            quota = tempBucket.quota,
            usedPercent = if (measureExact) (size.toDouble() / tempBucket.quota.toDouble()) * 100.0 else null,
            enforced = false,
            measured = measureExact
        )
    }
}
