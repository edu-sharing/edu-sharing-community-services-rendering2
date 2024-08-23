package org.edu_sharing.rendering.blobStorage

import org.apache.tika.mime.MimeTypes
import org.edu_sharing.rendering.config.annotation.ConditionalOnStorageByCustomer
import org.edu_sharing.rendering.dto.CacheObject
import org.springframework.stereotype.Component

@Component
@ConditionalOnStorageByCustomer
class BucketPerCustomerStrategy : BucketStrategy {
    override fun getStoragePath(cacheObject: CacheObject): String {
        var name = cacheObject.type.plus("/").plus(cacheObject.nodeId).plus("/").plus(cacheObject.hash)
        if (cacheObject.quality != null) {
            name = name.plus("_").plus(cacheObject.quality)
        }
        name = name.plus(getExtensionFromMimeType(cacheObject.mimeType))
        return name
    }

    override fun getBucket(cacheObject: CacheObject): String {
        return cacheObject.repoId?.replace("/","_") ?: ""
    }

    override fun prefixStaticPath(cacheObject: CacheObject, path: String): String {
        return "${cacheObject.type}/$path"
    }

    private fun getExtensionFromMimeType(mimeType: String): String {
        return if (mimeType.isNotBlank()) MimeTypes.getDefaultMimeTypes().forName(mimeType).extension else ""
    }
}
