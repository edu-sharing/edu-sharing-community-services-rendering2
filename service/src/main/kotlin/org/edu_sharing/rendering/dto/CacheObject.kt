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
) {
    fun deepCopy(): CacheObject {
        return CacheObject(
            nodeId = nodeId,
            type = type,
            size = size,
            hash = hash,
            quality = quality,
            mimeType = mimeType,
            repoId = repoId,
            version = version
        )
    }
}
