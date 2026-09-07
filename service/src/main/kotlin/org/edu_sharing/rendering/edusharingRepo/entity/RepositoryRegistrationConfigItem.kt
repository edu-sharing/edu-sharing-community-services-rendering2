package org.edu_sharing.rendering.edusharingRepo.entity

import org.springframework.boot.convert.DataSizeUnit
import org.springframework.util.unit.DataSize
import org.springframework.util.unit.DataUnit

data class RepositoryRegistrationConfigItem(
    val url: String,
    val username: String,
    val password: String,
    /** See [ExternalBucketConfig.quota] for the accepted format. */
    @param:DataSizeUnit(DataUnit.BYTES)
    var quota: DataSize = DataSize.ofBytes(0),
    val optionalModules: List<String> = listOf(),
    val module: MutableMap<String, ModuleSettings> = mutableMapOf(),
    val externalBuckets: ExternalBucketsConfig? = null
)

data class ModuleSettings(
    var credentials: Map<String, String> = emptyMap(),
    var cspHeader: String? = null
)

/**
 * Config-binding counterpart of [ExternalBucket]: accepts the quota as a [DataSize] (e.g. `10GB`,
 * `500MB` — a plain number without a suffix is still bytes) so `application.properties`/Helm/Compose
 * can specify human-readable sizes. Note this uses Spring's binary units (1KB = 1024 bytes), not
 * decimal (1000-based) ones. Converted to the byte-`Long`-based [ExternalBucket] via [toExternalBucket]
 * once bound — [ExternalBucket] itself stays plain bytes everywhere else (Mongo, the admin API,
 * business logic), so this conversion happens only here, at the config-binding boundary.
 */
data class ExternalBucketConfig(
    val name: String = "",
    @param:DataSizeUnit(DataUnit.BYTES)
    val quota: DataSize = DataSize.ofBytes(0)
) {
    fun toExternalBucket() = ExternalBucket(name = name, quota = quota.toBytes())
}

data class ExternalBucketsConfig(
    val renderingBucket: ExternalBucketConfig? = null,
    val tempBucket: ExternalBucketConfig? = null
) {
    fun toExternalBuckets() = ExternalBuckets(
        renderingBucket = renderingBucket?.toExternalBucket(),
        tempBucket = tempBucket?.toExternalBucket()
    )
}
