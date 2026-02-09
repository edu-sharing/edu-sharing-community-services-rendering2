package org.edu_sharing.rendering.cacheCleaner

data class BucketAggregation(
    val repoId: String,
    val buckets: List<BucketInfo>,
    val totalSize: Long
)
