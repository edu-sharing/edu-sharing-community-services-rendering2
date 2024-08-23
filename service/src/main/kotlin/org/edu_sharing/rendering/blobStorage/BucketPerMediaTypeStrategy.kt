package org.edu_sharing.rendering.blobStorage

import org.apache.tika.mime.MimeTypes
import org.edu_sharing.rendering.config.annotation.ConditionalOnStorageByMediaType
import org.edu_sharing.rendering.dto.CacheObject
import org.springframework.stereotype.Component

@Component
@ConditionalOnStorageByMediaType
class BucketPerMediaTypeStrategy : BucketStrategy {
    override fun getStoragePath(cacheObject: CacheObject): String {
        var name = cacheObject.nodeId.plus("/").plus(cacheObject.hash)
        if (cacheObject.quality != null) {
            name = name.plus("_").plus(cacheObject.quality)
        }
        name = name.plus(getExtensionFromMimeType(cacheObject.mimeType))
        return name
    }

    override fun getBucket(cacheObject: CacheObject): String {
        return cacheObject.type
    }

    override fun prefixStaticPath(cacheObject: CacheObject, path: String): String {
        return path
    }

    private fun getExtensionFromMimeType(mimeType: String): String {
        return if (mimeType.isNotBlank()) MimeTypes.getDefaultMimeTypes().forName(mimeType).extension else ""
    }
}
