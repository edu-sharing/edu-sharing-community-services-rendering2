package org.edu_sharing.rendering.dto.mapper

import org.edu_sharing.rendering.dto.AssetLinkParams
import org.edu_sharing.rendering.dto.CacheObject
import org.edu_sharing.rendering.dto.RenderDataRequest
import org.edu_sharing.rendering.entity.RenderingJob
import org.springframework.stereotype.Component

@Component
class Mapper {
    fun renderDataRequestToCacheObject(request: RenderDataRequest): CacheObject {
        return  CacheObject(
            nodeId = request.nodeId,
            type = request.type,
            hash = request.hash,
            size = request.size,
            mimeType = request.mimeType,
            version = request.version,
            repoId = request.repoId
        )
    }

    fun cacheObjectToRenderingJob(cacheObject: CacheObject): RenderingJob {
        return RenderingJob(
            esObjectType = cacheObject.type,
            esObjectId = cacheObject.nodeId,
            esHash = cacheObject.hash,
            mimeType = cacheObject.mimeType,
            repoId = cacheObject.repoId ?: "",
            version = cacheObject.version ?: "",
            size = cacheObject.size
        )
    }

    fun renderingJobToCacheObject(renderingJob: RenderingJob): CacheObject {
        return CacheObject(
            nodeId = renderingJob.esObjectId,
            type = renderingJob.esObjectType,
            hash = renderingJob.esHash,
            mimeType = renderingJob.mimeType,
            size =  renderingJob.size ?: -1,
            version = renderingJob.version,
            repoId = renderingJob.repoId
        )
    }

    fun assetLinkParamsToCacheObject(params: AssetLinkParams): CacheObject {
        return CacheObject(
            nodeId = params.nodeId,
            type = params.type,
            hash = params.hash,
            quality = if (params.quality != 0) params.quality else null,
            mimeType = params.mimeType
        )
    }
}