package org.edu_sharing.rendering.blobStorage.minio.bucket

import org.edu_sharing.rendering.dto.CacheObject

interface BucketStrategy {
    fun getStoragePath(cacheObject: CacheObject): String
    fun getBucket(cacheObject: CacheObject): String
    fun prefixStaticPath(cacheObject: CacheObject, path: String): String
}
