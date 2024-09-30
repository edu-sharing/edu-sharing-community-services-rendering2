package org.edu_sharing.rendering.storage.minio.bucket

import org.edu_sharing.rendering.core.dto.CacheObject
import org.springframework.stereotype.Component

@Component
@ConditionalOnStorageByCustomer
class BucketPerCustomerStrategy : BaseBucketStrategy() {

    override fun getCacheObjectRootPath(cacheObject: CacheObject): String {
        return "${cacheObject.type}/${cacheObject.nodeId}/${cacheObject.hash}"
    }

    override fun getBucket(cacheObject: CacheObject): String {
        return cacheObject.repoId.replace("/","_")
    }
}
