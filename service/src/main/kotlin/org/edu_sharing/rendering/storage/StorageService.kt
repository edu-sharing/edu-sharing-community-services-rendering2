package org.edu_sharing.rendering.storage

import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.core.dto.CachedObjectDetails
import org.edu_sharing.rendering.core.dto.ObjectLink
import java.io.InputStream

interface StorageService {
    /**
     * Stores [inputStream] as the final object. Implementations take ownership of the stream and
     * **must close it**, also when the upload fails — callers may pass a stream backed by pooled
     * Netty buffers that leak unless the stream is closed (see `S3StorageService.putObjectStreaming`).
     */
    fun putObject(cacheObject: CacheObject, inputStream: InputStream, metadata: Map<String, String> = emptyMap())
    fun getObjectLink(cacheObject: CacheObject): Pair<ObjectLink, Long>
    fun removeTempObject(cacheObject: CacheObject)
    fun removeObjects(cacheObjects: List<CacheObject>, isTemp: Boolean = false)
    fun getObjectStream(cacheObject: CacheObject, isTemp: Boolean = false): InputStream
    fun getObjectChunkStream(cacheObject: CacheObject, length: Long, offset: Long, isTemp: Boolean = false): InputStream
    /** Stores [inputStream] as the conversion input. Same ownership contract as [putObject]. */
    fun putTempFile(cacheObject: CacheObject, inputStream: InputStream)
    fun getFileProperties(cacheObject: CacheObject): CachedObjectDetails
    fun getStorageInfo(): List<StorageInfo>
    fun objectExists(cacheObject: CacheObject): Boolean
    fun isStoringByRepoId(): Boolean
    fun getDirectorySize(bucket: String, directory: String): Long
    fun getUsedSpace(repoId: String): Pair<Long, List<String>>
    fun getBuckets(): List<String>
}
