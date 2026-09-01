package org.edu_sharing.rendering.storage

import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.core.dto.CachedObjectDetails
import org.edu_sharing.rendering.core.dto.ObjectLink
import java.io.InputStream

interface StorageService {
    /**
     * Stores the object read from [streamProvider] as the final object. [streamProvider] must return
     * a **fresh, unread stream on every call** — implementations may invoke it more than once (e.g.
     * to retry an upload after a transient storage error) and close every stream it returns, also
     * when the upload fails — a returned stream may be backed by pooled Netty buffers that leak
     * unless closed (see `S3StorageService.putObjectStreaming`).
     */
    fun putObject(cacheObject: CacheObject, streamProvider: () -> InputStream, metadata: Map<String, String> = emptyMap())
    fun getObjectLink(cacheObject: CacheObject): Pair<ObjectLink, Long>
    fun removeTempObject(cacheObject: CacheObject)
    fun removeObjects(cacheObjects: List<CacheObject>, isTemp: Boolean = false)
    fun getObjectStream(cacheObject: CacheObject, isTemp: Boolean = false): InputStream
    fun getObjectChunkStream(cacheObject: CacheObject, length: Long, offset: Long, isTemp: Boolean = false): InputStream
    /** Stores the conversion input read from [streamProvider]. Same contract as [putObject]. */
    fun putTempFile(cacheObject: CacheObject, streamProvider: () -> InputStream)
    fun getFileProperties(cacheObject: CacheObject): CachedObjectDetails
    fun getStorageInfo(): List<StorageInfo>
    fun objectExists(cacheObject: CacheObject): Boolean
    fun isStoringByRepoId(): Boolean
    fun getDirectorySize(bucket: String, directory: String): Long
    fun getUsedSpace(repoId: String): Pair<Long, List<String>>
    fun getBuckets(): List<String>
}
