package org.edu_sharing.rendering.dto

data class CacheObject(
    var nodeId: String,
    var type: String,
    var size: Long = -1,
    val hash: String,
    var quality: Int? = null,
    var mimeType: String = "",
    val repoId: String? = null,
    val version: String? = null
)
