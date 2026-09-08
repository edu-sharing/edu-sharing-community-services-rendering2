package org.edu_sharing.rendering.edusharingRepo.entity

data class ExternalBucket(
    var name: String = "",
    /** Quota in bytes; 0 = no limit. See [ExternalBucketConfig] for the config-facing, human-readable form. */
    var quota: Long = 0
) {
    val isConfigured: Boolean get() = name.isNotBlank()
}

data class ExternalBuckets(
    var renderingBucket: ExternalBucket? = null,
    var tempBucket: ExternalBucket? = null
)
