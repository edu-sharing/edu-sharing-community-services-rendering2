package org.edu_sharing.rendering.edusharingRepo.dto

/**
 * Aggregierte Storage-Nutzung eines einzelnen Repos für das Admin-Dashboard.
 *
 * @property quota Repo-Quota in Bytes oder `null`, wenn kein Limit konfiguriert ist (Quota 0).
 * @property usedPercent Belegung in Prozent (0..100) oder `null`, wenn kein Limit gesetzt ist.
 * @property exact `true`, wenn die Werte exakt aus S3 neu berechnet wurden, sonst aus dem Tracking.
 */
data class StorageUsageInfo(
    val repoId: String,
    val totalSize: Long,
    val quota: Long?,
    val usedPercent: Double?,
    val exact: Boolean,
    val buckets: List<BucketUsageInfo>
)

data class BucketUsageInfo(
    val name: String,
    val size: Long
)
