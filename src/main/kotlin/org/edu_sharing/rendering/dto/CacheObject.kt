package org.edu_sharing.rendering.dto

data class CacheObject(
    val nodeId: String,
    val version: String,
    val type: String,
    var size: Long,
    val hash: String,
    val extension: String,
    var quality: Int? = null,
    var mimeType: String
)
