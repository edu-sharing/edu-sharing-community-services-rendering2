package org.edu_sharing.rendering.cacheCleaner

/**
 * Aggregierte Asset-Kennzahlen je Typ innerhalb eines Repos (für "Löschen nach Typ").
 */
data class AssetTypeAggregation(
    val type: String,
    val count: Long,
    val totalSize: Long
)
