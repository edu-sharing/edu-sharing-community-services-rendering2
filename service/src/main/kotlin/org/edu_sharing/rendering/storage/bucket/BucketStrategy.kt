package org.edu_sharing.rendering.storage.bucket

import org.edu_sharing.rendering.core.dto.CacheObject

interface BucketStrategy {
    fun getStoragePath(cacheObject: CacheObject): String
    fun getCacheObjectRootPath(cacheObject: CacheObject): String
    fun getBucket(cacheObject: CacheObject): String
    fun getStoragePath(cacheObject: CacheObject, path: String): String
    fun getExtensionFromMimeType(mimeType: String): String
    fun getCacheObjectFromStaticPath(path: String): Pair<CacheObject, String>
    fun prefixStaticPath(cacheObject: CacheObject, path: String): String
    fun getCacheObjectFromStoragePath(bucket: String, storagePath: String): CacheObject?
    fun isPrefixBased(): Boolean
}
