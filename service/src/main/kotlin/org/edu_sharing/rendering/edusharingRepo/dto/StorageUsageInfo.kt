package org.edu_sharing.rendering.edusharingRepo.dto

/**
 * Aggregated storage usage of a single repo for the admin dashboard.
 *
 * @property quota Repo-wide quota in bytes, or `null` if no limit is configured (quota 0). Stays
 *   present even once individual buckets ([buckets]) already report their own quotas — it is only
 *   relevant as long as no single bucket quota is known for this repo.
 * @property usedPercent Usage in percent (0..100), or `null` if no limit is set.
 * @property exact `true` if the requested bucket (see `bucket` param on
 *   [org.edu_sharing.rendering.edusharingRepo.AdminStorageController.getStorageUsage]) was recomputed
 *   exactly from S3; all other buckets in [buckets] still come from tracking regardless.
 */
data class StorageUsageInfo(
    val repoId: String,
    val totalSize: Long,
    val quota: Long?,
    val usedPercent: Double?,
    val exact: Boolean,
    val buckets: List<BucketUsageInfo>
)

/**
 * @property quota Bucket quota in bytes, or `null` if no limit is known for this bucket.
 * @property enforced `true` if the CacheCleaner enforces this quota (e.g. rendering/content bucket);
 *   the temp bucket never has an [org.edu_sharing.rendering.storage.StorageManager] and is therefore always `false`.
 * @property measured `false` if the size isn't reliable — currently only the temp bucket without
 *   `exact=true` (it is barely tracked, see [org.edu_sharing.rendering.edusharingRepo.AdminStorageController]).
 */
data class BucketUsageInfo(
    val name: String,
    val size: Long,
    val quota: Long? = null,
    val usedPercent: Double? = null,
    val enforced: Boolean = true,
    val measured: Boolean = true
)
