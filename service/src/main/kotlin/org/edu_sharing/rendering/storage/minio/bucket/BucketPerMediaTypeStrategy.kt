package org.edu_sharing.rendering.storage.minio.bucket

import org.edu_sharing.rendering.core.dto.CacheObject
import org.springframework.stereotype.Component

@Component
@ConditionalOnStorageByMediaType
class BucketPerMediaTypeStrategy : BaseBucketStrategy() {
    override fun getCacheObjectRootPath(cacheObject: CacheObject): String {
        return "${cacheObject.nodeId}/${cacheObject.hash}"
    }

    override fun getBucket(cacheObject: CacheObject): String {
        return cacheObject.type
    }
}
