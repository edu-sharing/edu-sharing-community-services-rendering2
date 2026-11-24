package org.edu_sharing.rendering.modules.h5p.lumi.dto

import com.fasterxml.jackson.annotation.JsonProperty
import org.edu_sharing.rendering.edusharingRepo.entity.ExternalBucket

data class LumiBucketInfo(
    @JsonProperty("contentBucket")
    val contentBucket: String,
    /** Quota in bytes for [contentBucket]; `null`/`0` if lumi reports no limit. */
    @JsonProperty("contentBucketQuota")
    val contentBucketQuota: Long? = null,
    /**
     * Key prefix of lumi's content storage inside [contentBucket]; `null`/empty when lumi owns the
     * whole bucket (and for a lumi that predates key prefixes).
     */
    @JsonProperty("contentKeyPrefix")
    val contentKeyPrefix: String? = null,
    /**
     * Present only while lumi runs the per-package H5P library cache; `null` on the shared global
     * library storage, which has nothing of its own to account for.
     */
    @JsonProperty("libraryCache")
    val libraryCache: LumiLibraryCacheInfo? = null
) {
    /** [contentKeyPrefix] normalized like the service's own prefixes: `""` or `a/b/`. */
    fun normalizedContentKeyPrefix(): String = ExternalBucket.normalizeKeyPrefix(contentKeyPrefix)

    /**
     * The name under which the service identifies lumi's storage (tracking bucket, owner check, quota
     * key, admin rows). Just [contentBucket] without a prefix; a bucket name never contains `/`, so
     * the prefixed form cannot collide with the name of a real bucket. Use [contentBucket] plus
     * [normalizedContentKeyPrefix] for S3 calls only.
     */
    fun scopeName(): String {
        val prefix = normalizedContentKeyPrefix()
        return if (prefix.isEmpty()) contentBucket else "$contentBucket/${prefix.trimEnd('/')}"
    }
}

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
