package org.edu_sharing.rendering.cacheCleaner

interface CustomTrackingEntryRepository {
    /**
     * Aggregiert die Tracking-Einträge eines Repos nach nodeId (paginiert). Pro nodeId werden
     * die Daten der neuesten Version (höchstes lastAccessed) sowie Anzahl und Gesamtgröße aller
     * Versionen geliefert. Optionale Freitextsuche (`search`, Regex über nodeId/type/hash) und
     * serverseitige Sortierung (`sort`-Spaltenschlüssel + `dir` asc/desc). Optional auf einen
     * Zeitraum eingegrenzt (`accessedFrom`/`accessedTo`, epoch-ms, inklusiv, gegen `lastAccessed`).
     */
    fun aggregateNodes(
        repoId: String,
        type: String?,
        search: String?,
        sort: String?,
        dir: String,
        accessedFrom: Long?,
        accessedTo: Long?,
        page: Int,
        size: Int
    ): NodeAggregationResult

    /**
     * Aggregiert die Asset-Kennzahlen je Typ innerhalb eines Repos. Optionale Freitextsuche
     * (`search`, Regex über den Typnamen) und serverseitige Sortierung (`sort` + `dir`).
     */
    fun aggregateTypes(repoId: String, search: String?, sort: String?, dir: String): List<AssetTypeAggregation>
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
