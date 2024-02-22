package org.edu_sharing.rendering.blobStorage

import io.minio.GetObjectResponse
import org.edu_sharing.rendering.dto.CacheObject
import java.io.FileInputStream

interface StorageService {
    fun putObject(cacheObject: CacheObject, inputStream: FileInputStream)
    fun getObjectLink(cacheObject: CacheObject): String
    fun removeObject(cacheObject: CacheObject)
    fun getObjectStream(cacheObject: CacheObject, isTemp: Boolean = false): GetObjectResponse
    fun putTempFile(cacheObject: CacheObject, inputStream: FileInputStream)
}