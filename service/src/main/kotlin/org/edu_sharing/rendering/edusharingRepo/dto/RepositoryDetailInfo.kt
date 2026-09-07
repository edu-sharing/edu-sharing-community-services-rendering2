package org.edu_sharing.rendering.edusharingRepo.dto

/**
 * Vollständige Eigenschaften eines registrierten Repos für die Admin-Detailansicht.
 *
 * Bewusst **ohne** Klartext-Secrets: pro Modul werden nur die Credential-*Schlüssel*
 * ausgegeben, nicht deren Werte; vom Public Key nur eine gekürzte Vorschau.
 */
data class RepositoryDetailInfo(
    val repoId: String,
    val url: String,
    val domains: List<String>,
    val optionalModules: List<String>,
    val modules: Map<String, ModuleSettingInfo>,
    /** Repo-wide quota (bytes); only relevant as long as no bucket quota is configured. */
    val quota: Long,
    val renderingBucket: String?,
    val renderingBucketQuota: Long?,
    val tempBucket: String?,
    val tempBucketQuota: Long?,
    /** H5P/lumi content bucket; name + quota come from lumi itself, not from this registration. */
    val contentBucket: String?,
    val contentBucketQuota: Long?,
    val allowedOrigins: List<String>,
    val allowedOriginPatterns: List<String>,
    val lastAllowedOriginSync: Long,
    val signingAlgorithm: String,
    val publicKeyPreview: String
)

data class ModuleSettingInfo(
    val credentialKeys: List<String>,
    val cspHeader: String?
)
