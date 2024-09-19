package org.edu_sharing.rendering.blobStorage

import org.edu_sharing.rendering.dto.CacheObject
import org.edu_sharing.rendering.dto.CachedObjectDetails
import org.edu_sharing.rendering.dto.ObjectLink
import java.io.InputStream

interface StorageService {
    fun putObject(cacheObject: CacheObject, inputStream: InputStream, metadata: Map<String, String> = emptyMap())
    fun getObjectLink(cacheObject: CacheObject): ObjectLink
    fun removeObject(cacheObject: CacheObject, isTemp: Boolean = false)
    fun getObjectStream(cacheObject: CacheObject, isTemp: Boolean = false): InputStream
    fun getObjectChunkStream(cacheObject: CacheObject, length: Long, offset: Long, isTemp: Boolean = false): InputStream
    fun putTempFile(cacheObject: CacheObject, inputStream: InputStream)
    fun getFileProperties(cacheObject: CacheObject): CachedObjectDetails
    fun getStorageInfo(): List<StorageInfo>
    fun freeStorage(storageInfo: StorageInfo, lowerThreshold: Float)
    fun objectExists(cacheObject: CacheObject): Boolean
}
