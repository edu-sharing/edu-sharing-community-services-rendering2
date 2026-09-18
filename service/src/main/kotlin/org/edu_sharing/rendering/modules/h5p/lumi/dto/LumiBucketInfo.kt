package org.edu_sharing.rendering.modules.h5p.lumi.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class LumiBucketInfo(
    @JsonProperty("contentBucket")
    val contentBucket: String,
    /** Quota in bytes for [contentBucket]; `null`/`0` if lumi reports no limit. */
    @JsonProperty("contentBucketQuota")
    val contentBucketQuota: Long? = null,
    /**
     * Present only while lumi runs the per-package H5P library cache; `null` on the shared global
     * library storage, which has nothing of its own to account for.
     */
    @JsonProperty("libraryCache")
    val libraryCache: LumiLibraryCacheInfo? = null
)

/**
 * Fill level of lumi's per-package H5P library cache.
 *
 * Unlike the S3 buckets this is a filesystem inside lumi, and it holds the only copy of those
 * packages' libraries - freeing space means deleting whole H5P content objects (content, libraries
 * and lumi's node mapping together), which is what makes them re-importable on the next request.
 */
data class LumiLibraryCacheInfo(
    /** Nullable for the same reason as [LumiContentResponse.libraryBytes]: absent fields must not
     *  fail deserialization of an otherwise valid response. */
    @JsonProperty("usedBytes")
    val usedBytes: Long? = null,
    /** Configured limit in bytes; `null`/`0` if lumi reports no limit. */
    @JsonProperty("quota")
    val quota: Long? = null
)
