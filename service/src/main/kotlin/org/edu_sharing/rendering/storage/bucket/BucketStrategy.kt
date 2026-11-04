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
    fun isManagedBucket(bucket: String, repoId: String): Boolean
    fun getTempBucket(repoId: String): String = "temp"

    /** Object key of a not yet converted original inside the temp bucket (see [getTempBucket]). */
    fun getTempStoragePath(cacheObject: CacheObject): String

    /** Key prefix all rendering objects of [repoId] live under in their bucket; empty = bucket root. */
    fun getRenderingKeyPrefix(repoId: String): String = ""

    /** Key prefix all temp objects of [repoId] live under in the temp bucket; empty = bucket root. */
    fun getTempKeyPrefix(repoId: String): String = ""
}
