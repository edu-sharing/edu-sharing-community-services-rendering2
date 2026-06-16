package org.edu_sharing.rendering.storage.bucket

import org.edu_sharing.rendering.core.dto.CacheObject
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
@ConditionalOnStorageByCustomer
class BucketPerCustomerStrategy : BaseBucketStrategy() {
    private val prefix = "rs2-"
    private val log = LoggerFactory.getLogger(javaClass)

    override fun getCacheObjectRootPath(cacheObject: CacheObject): String {
        return "${cacheObject.type}/${cacheObject.nodeId}/${cacheObject.hash}"
    }

    override fun getBucket(cacheObject: CacheObject): String {
        val bucket = "$prefix${cacheObject.repoId}"
        log.debug("Resolved bucket (per-customer strategy): repoId=${cacheObject.repoId}, bucket=$bucket")
        return bucket
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

    override fun isManagedBucket(bucket: String, repoId: String): Boolean {
        return bucket.startsWith(prefix)
    }
}
