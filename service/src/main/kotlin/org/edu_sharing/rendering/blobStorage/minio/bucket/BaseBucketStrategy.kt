package org.edu_sharing.rendering.blobStorage.minio.bucket

import org.apache.tika.mime.MimeTypes
import org.edu_sharing.rendering.dto.CacheObject

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

    override fun prefixStaticPath(cacheObject: CacheObject, path: String): String {
        return "${getCacheObjectRootPath(cacheObject)}/$path"
    }

    override fun getExtensionFromMimeType(mimeType: String): String {
        return if (mimeType.isNotBlank()) MimeTypes.getDefaultMimeTypes().forName(mimeType).extension else ""
    }
}
