package org.edu_sharing.rendering.blobStorage

import io.minio.GetObjectResponse
import org.edu_sharing.rendering.dto.CacheObject
import java.io.FileInputStream
import java.io.InputStream

interface StorageService {
    fun putObject(cacheObject: CacheObject, inputStream: InputStream)
    fun getObjectLink(cacheObject: CacheObject): String
    fun removeObject(cacheObject: CacheObject)
    fun getObjectStream(cacheObject: CacheObject, isTemp: Boolean = false): GetObjectResponse
    fun putTempFile(cacheObject: CacheObject, inputStream: FileInputStream)
}