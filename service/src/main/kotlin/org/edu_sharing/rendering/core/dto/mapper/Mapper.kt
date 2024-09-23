package org.edu_sharing.rendering.core.dto.mapper

import org.edu_sharing.rendering.asset.dto.AssetLinkParams
import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.core.dto.RenderDataRequest
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.springframework.stereotype.Component

@Component
class Mapper {
    fun renderDataRequestToCacheObject(request: RenderDataRequest): CacheObject {
        return CacheObject(
            nodeId = request.nodeId,
            type = request.type,
            hash = request.hash,
            size = request.size,
            mimeType = request.mimeType,
            version = request.version,
            repoId = request.repoId
        )
    }

    fun cacheObjectToRenderingJob(cacheObject: CacheObject, module: String, isConversionType: Boolean = false): RenderingJob {
        return RenderingJob(
            esObjectType = cacheObject.type,
            esObjectId = cacheObject.nodeId,
            esHash = cacheObject.hash,
            mimeType = cacheObject.mimeType,
            repoId = cacheObject.repoId,
            nodeVersion = cacheObject.version ?: "",
            size = cacheObject.size,
            module = module,
            conversionType = isConversionType
        )
    }

    fun renderDataRequestToRenderingJob(request: RenderDataRequest, module: String): RenderingJob {
        return RenderingJob(
            esObjectId = request.nodeId,
            esObjectType = request.type,
            esHash = request.hash,
            mimeType = request.mimeType,
            repoId = request.repoId,
            nodeVersion = request.version,
            size = request.size,
            module = module
        )
    }

    fun renderingJobToCacheObject(renderingJob: RenderingJob): CacheObject {
        return CacheObject(
            nodeId = renderingJob.esObjectId,
            type = renderingJob.esObjectType,
            hash = renderingJob.esHash,
            mimeType = renderingJob.mimeType,
            size = renderingJob.size ?: -1,
            version = renderingJob.nodeVersion,
            repoId = renderingJob.repoId
        )
    }

    fun assetLinkParamsToCacheObject(params: AssetLinkParams): CacheObject {
        return CacheObject(
            repoId = params.repoId,
            nodeId = params.nodeId,
            type = params.type,
            hash = params.hash,
            quality = if (params.quality != 0) params.quality else null,
            mimeType = params.mimeType
        )
    }
}
