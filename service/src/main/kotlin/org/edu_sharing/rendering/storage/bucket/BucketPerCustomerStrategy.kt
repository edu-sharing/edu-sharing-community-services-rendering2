package org.edu_sharing.rendering.storage.bucket

import org.edu_sharing.rendering.core.dto.CacheObject
import org.springframework.stereotype.Component

@Component
@ConditionalOnStorageByCustomer
class BucketPerCustomerStrategy : BaseBucketStrategy() {

    override fun getCacheObjectRootPath(cacheObject: CacheObject): String {
        return "${cacheObject.type}/${cacheObject.nodeId}/${cacheObject.hash}"
    }

    override fun getBucket(cacheObject: CacheObject): String {
        return "rs2-${cacheObject.repoId}"
    }

    override fun prefixStaticPath(
        cacheObject: CacheObject,
        path: String
    ): String {
        val storagePath = getStoragePath(cacheObject, path)
        return "/${cacheObject.repoId}/${storagePath.trimStart('/')}"
    }

    override fun getCacheObjectFromStoragePath(
        bucket: String,
        storagePath: String
    ): CacheObject? {
        val (type, nodeId, hash) = storagePath.trimStart('/').split("/", limit = 3)
        return CacheObject.of(repoId = bucket, type = type, nodeId = nodeId, hash = hash)
    }
}
