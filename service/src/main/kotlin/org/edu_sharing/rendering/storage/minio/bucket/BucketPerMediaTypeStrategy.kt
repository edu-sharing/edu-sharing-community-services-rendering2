package org.edu_sharing.rendering.storage.minio.bucket

import org.edu_sharing.rendering.core.dto.CacheObject
import org.springframework.stereotype.Component

@Component
@ConditionalOnStorageByMediaType
class BucketPerMediaTypeStrategy() : BaseBucketStrategy() {
    override fun getCacheObjectRootPath(cacheObject: CacheObject): String {
        return "${cacheObject.nodeId}/${cacheObject.hash}"
    }

    override fun getBucket(cacheObject: CacheObject): String {
        return "rs2-${cacheObject.type}"
    }

    override fun prefixStaticPath(
        cacheObject: CacheObject,
        path: String
    ): String {
        val storagePath = getStoragePath(cacheObject, path)
        return "/${cacheObject.repoId}/${cacheObject.type}/${storagePath.trimStart('/')}"
    }

    override fun getCacheObjectFromStoragePath(
        bucket: String,
        storagePath: String
    ): CacheObject? {
        val (nodeId, hash) = storagePath.trimStart('/').split("/", limit = 2)
        return CacheObject.of(repoId = bucket, nodeId = nodeId, hash = hash, type = "")
    }
}
