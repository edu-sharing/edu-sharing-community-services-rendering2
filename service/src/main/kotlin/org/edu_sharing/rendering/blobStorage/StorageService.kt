package org.edu_sharing.rendering.blobStorage

import io.minio.GetObjectResponse
import io.minio.StatObjectResponse
import org.edu_sharing.rendering.dto.CacheObject
import org.edu_sharing.rendering.dto.CachedObjectDetails
import org.edu_sharing.rendering.dto.ObjectLink
import java.io.InputStream

interface StorageService {
    fun putObject(cacheObject: CacheObject, inputStream: InputStream, metadata: Map<String, String> = emptyMap())
    fun putObject(cacheObject: CacheObject, inputStream: InputStream, targetPath: String, metadata: Map<String, String> = emptyMap())
    fun getObjectLink(cacheObject: CacheObject): ObjectLink
    fun getObjectLink(path: String): ObjectLink
    fun removeObject(cacheObject: CacheObject, isTemp: Boolean = false)
    fun getObjectStream(cacheObject: CacheObject, isTemp: Boolean = false): InputStream
    fun getObjectStream(bucket: String, path: String): InputStream
    fun getObjectChunkStream(cacheObject: CacheObject, isTemp: Boolean = false, offset: Long, length: Long): InputStream
    fun getObjectChunkStream(bucket: String, path: String, offset: Long, length: Long): InputStream
    fun putTempFile(cacheObject: CacheObject, inputStream: InputStream)
    fun getFileProperties(cacheObject: CacheObject): CachedObjectDetails
    fun getFileProperties(bucket: String, path: String): CachedObjectDetails
}