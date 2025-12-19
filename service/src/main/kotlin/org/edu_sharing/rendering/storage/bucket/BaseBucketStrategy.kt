package org.edu_sharing.rendering.storage.bucket

import org.apache.tika.mime.MimeTypes
import org.edu_sharing.rendering.core.dto.CacheObject

abstract class BaseBucketStrategy : BucketStrategy {
    override fun getStoragePath(cacheObject: CacheObject): String {
        val sb = StringBuilder()
        sb.append(getCacheObjectRootPath(cacheObject))
        if (cacheObject.quality != null) {
            sb.append("_${cacheObject.quality}")
        }
        sb.append(getExtensionFromMimeType(cacheObject.mimeType))
        return sb.toString()
    }

    override fun getStoragePath(cacheObject: CacheObject, path: String): String {
        return "/${getCacheObjectRootPath(cacheObject)}/${path.trimStart('/')}"
    }

    override fun getExtensionFromMimeType(mimeType: String): String {
        return if (mimeType.isNotBlank()) MimeTypes.getDefaultMimeTypes().forName(mimeType).extension else ""
    }

    override fun getCacheObjectFromStaticPath(path: String): Pair<CacheObject, String> {
        val pathVars = path.trimStart('/').split("/", limit = 5)
        if (pathVars.size < 5) {
            throw IllegalArgumentException("Invalid number of path arguments")
        }
        return Pair(CacheObject.of(
            repoId = pathVars[0],
            type = pathVars[1],
            nodeId = pathVars[2],
            hash = pathVars[3],
        ), "/${pathVars[4]}")
    }
}
