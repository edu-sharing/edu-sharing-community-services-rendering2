package org.edu_sharing.rendering.storage.minio.bucket

import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.modules.ModuleRegistry
import org.edu_sharing.rendering.modules.RenderModule
import org.springframework.stereotype.Component

@Component
@ConditionalOnStorageByMediaType
class BucketPerMediaTypeStrategy(
    private val moduleRegistry: ModuleRegistry
) : BaseBucketStrategy() {
    override fun getCacheObjectRootPath(cacheObject: CacheObject): String {
        return "${cacheObject.nodeId}/${cacheObject.hash}"
    }

    override fun getBucket(cacheObject: CacheObject): String {
        val module = moduleRegistry.getRenderModule<RenderModule>(cacheObject)
        return "rs2_${module.module()}"
    }

    override fun prefixStaticPath(
        cacheObject: CacheObject,
        path: String
    ): String {
        val storagePath = getStoragePath(cacheObject, path)
        return "/${cacheObject.repoId}/${cacheObject.type}/${storagePath.trimStart('/')}"
    }
}
