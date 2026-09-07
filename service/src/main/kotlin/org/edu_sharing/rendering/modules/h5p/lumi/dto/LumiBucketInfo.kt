package org.edu_sharing.rendering.modules.h5p.lumi.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class LumiBucketInfo(
    @JsonProperty("contentBucket")
    val contentBucket: String,
    /** Quota in bytes for [contentBucket]; `null`/`0` if lumi reports no limit. */
    @JsonProperty("contentBucketQuota")
    val contentBucketQuota: Long? = null
)
