package org.edu_sharing.rendering.dto

data class CacheObject(
    val nodeId: String,
    val type: String,
    var size: Long = 0,
    val hash: String,
    var quality: Int? = null,
    var mimeType: String,
    var origin: String? = null
)
