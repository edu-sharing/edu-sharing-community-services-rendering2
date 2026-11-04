package org.edu_sharing.rendering.edusharingRepo.entity

data class ExternalBucket(
    var name: String = "",
    /** Quota in bytes; 0 = no limit. See [ExternalBucketConfig] for the config-facing, human-readable form. */
    var quota: Long = 0,
    /**
     * Optional key prefix inside the bucket, e.g. `rendering` or `tenant-a/rendering`. Lets several
     * repositories, or the rendering and the temp bucket of one repository, share a single S3 bucket:
     * every object is stored below it and the quota measurement is scoped to it. Leading/trailing
     * slashes are ignored; empty = objects at the bucket root (the previous behaviour). Use
     * [normalizedKeyPrefix] to build keys.
     */
    var keyPrefix: String = ""
) {
    val isConfigured: Boolean get() = name.isNotBlank()

    /** [keyPrefix] without surrounding slashes and with exactly one trailing slash, or empty. */
    fun normalizedKeyPrefix(): String = normalizeKeyPrefix(keyPrefix)

    companion object {
        fun normalizeKeyPrefix(raw: String?): String {
            val trimmed = raw.orEmpty().trim().trim('/')
            return if (trimmed.isEmpty()) "" else "$trimmed/"
        }
    }
}

data class ExternalBuckets(
    var renderingBucket: ExternalBucket? = null,
    var tempBucket: ExternalBucket? = null
)
