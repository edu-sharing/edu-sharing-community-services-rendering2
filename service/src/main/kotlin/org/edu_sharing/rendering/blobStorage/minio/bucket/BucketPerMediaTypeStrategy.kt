package org.edu_sharing.rendering.blobStorage.minio.bucket

import org.edu_sharing.rendering.config.annotation.ConditionalOnStorageByMediaType
import org.edu_sharing.rendering.dto.CacheObject
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
