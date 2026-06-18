package org.edu_sharing.rendering.cacheCleaner

interface CustomTrackingEntryRepository {
    /**
     * Aggregiert die Tracking-Einträge eines Repos nach nodeId (paginiert). Pro nodeId werden
     * die Daten der neuesten Version (höchstes lastAccessed) sowie Anzahl und Gesamtgröße aller
     * Versionen geliefert.
     */
    fun aggregateNodes(repoId: String, type: String?, page: Int, size: Int): NodeAggregationResult
}

data class NodeAggregationResult(
    val content: List<NodeAggregation>,
    val total: Long
)

data class NodeAggregation(
    val nodeId: String,
    val type: String,
    val bucket: String,
    val hash: String,
    val size: Long,
    val lastAccessed: Long,
    val versionCount: Long,
    val totalSize: Long
)
