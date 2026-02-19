package org.edu_sharing.rendering.edusharingRepo.dto

data class CacheUsageInfo(
    val managedBuckets: List<String>,
    val actualSize: Long,
    val trackedSize: Long,
    val discrepancy: Long
)
