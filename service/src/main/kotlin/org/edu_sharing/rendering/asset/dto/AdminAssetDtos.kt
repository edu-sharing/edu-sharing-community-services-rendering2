package org.edu_sharing.rendering.asset.dto

/**
 * Ein einzelnes, getracktes S3-Asset eines Repos in der Admin-Übersicht.
 */
data class AssetInfo(
    val nodeId: String,
    val hash: String,
    val type: String,
    val bucket: String,
    val size: Long,
    val lastAccessed: Long
)

/**
 * Paginierte Asset-Liste eines Repos (eigener Typ statt Spring `Page` für einen stabilen
 * OpenAPI-Contract).
 */
data class AssetPage(
    val content: List<AssetInfo>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)

/**
 * Ein nach nodeId gebündelter Asset-Eintrag: zeigt die neueste Version sowie Anzahl/Größe
 * aller Versionen dieser nodeId. Die Einzelversionen werden separat über /assets/versions geladen.
 */
data class AssetNode(
    val nodeId: String,
    val type: String,
    val bucket: String,
    val hash: String,
    val size: Long,
    val lastAccessed: Long,
    val versionCount: Long,
    val totalSize: Long
)

/** Paginierte, nach nodeId gebündelte Asset-Liste. */
data class AssetNodePage(
    val content: List<AssetNode>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)

/**
 * Asset-Kennzahlen je Typ – Grundlage für "Löschen nach Typ".
 */
data class AssetTypeInfo(
    val type: String,
    val count: Long,
    val totalSize: Long
)

/**
 * Ergebnis einer Bulk-Löschung (nach Typ / alle).
 */
data class AssetDeletionResult(
    val deleted: Long
)
