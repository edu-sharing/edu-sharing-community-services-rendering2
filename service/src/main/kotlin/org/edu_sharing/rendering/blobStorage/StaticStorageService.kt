package org.edu_sharing.rendering.blobStorage

import org.edu_sharing.rendering.dto.CacheObject
import org.edu_sharing.rendering.dto.CachedObjectDetails
import org.edu_sharing.rendering.dto.ObjectLink
import java.io.InputStream

interface StaticStorageService: StorageService {
    fun putObject(
        cacheObject: CacheObject,
        inputStream: InputStream,
        targetPath: String,
        metadata: Map<String, String> = emptyMap()
    )

    // Used for static files (zip html)
    fun getObjectLink(cacheObject: CacheObject, path: String): ObjectLink

    // Used for static files (zip html)
    fun getObjectStream(cacheObject: CacheObject, path: String): InputStream

    // Used for static files (zip html)
    fun getObjectChunkStream(cacheObject: CacheObject, path: String, offset: Long, length: Long): InputStream

    // Used for static files (zip html)
    fun getFileProperties(cacheObject: CacheObject, path: String): CachedObjectDetails

    fun objectExists(cacheObject: CacheObject, path: String): Boolean
}
