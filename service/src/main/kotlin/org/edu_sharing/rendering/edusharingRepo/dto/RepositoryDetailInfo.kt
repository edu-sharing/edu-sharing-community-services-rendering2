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
    val quota: Long,
    val renderingBucket: String?,
    val tempBucket: String?,
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
