package org.edu_sharing.rendering.blobStorage

import io.minio.GetObjectResponse
import io.minio.StatObjectResponse
import org.edu_sharing.rendering.dto.CacheObject
import java.io.FileInputStream
import java.io.InputStream

interface StorageService {
    fun putObject(cacheObject: CacheObject, inputStream: InputStream, metadata: Map<String, String> = emptyMap())
    fun getObjectLink(cacheObject: CacheObject): String
    fun removeObject(cacheObject: CacheObject)
    fun getObjectStream(cacheObject: CacheObject, isTemp: Boolean = false): GetObjectResponse
    fun getObjectChunkStream(cacheObject: CacheObject, isTemp: Boolean = false, offset: Long, length: Long): GetObjectResponse
    fun putTempFile(cacheObject: CacheObject, inputStream: FileInputStream)
    fun isObjectExisting(cacheObject: CacheObject): Boolean
    fun getFileProperties(cacheObject: CacheObject): StatObjectResponse
}