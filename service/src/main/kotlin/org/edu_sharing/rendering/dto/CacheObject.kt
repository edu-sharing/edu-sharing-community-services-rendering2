package org.edu_sharing.rendering.dto

data class CacheObject(
    var nodeId: String,
    var type: String,
    var size: Long = -1,
    val hash: String,
    var quality: Int? = null,
    var mimeType: String = "",
    val repoId: String,
    val version: String? = null
) {

    companion object{
        fun of(repoId: String, nodeId: String, hash: String, type: String): CacheObject{
            return CacheObject(nodeId = nodeId, type = type, hash = hash, repoId = repoId)
        }
    }

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
