package org.edu_sharing.rendering.dto.mapper

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
            origin = request.origin
        )
    }

    fun cacheObjectToRenderingJob(cacheObject: CacheObject): RenderingJob {
        return RenderingJob(
            esObjectType = cacheObject.type,
            esObjectId = cacheObject.nodeId,
            esHash = cacheObject.hash,
            mimeType = cacheObject.mimeType,
            origin = cacheObject.origin.toString()
        )
    }

    fun renderingJobToCacheObject(renderingJob: RenderingJob): CacheObject {
        return CacheObject(
            nodeId = renderingJob.esObjectId,
            type = renderingJob.esObjectType,
            hash = renderingJob.esHash,
            mimeType = renderingJob.mimeType,
        )
    }

    fun fileRequestParamToCacheObject(requestParam: String): CacheObject {
        // I expect: "type_objectid_hash_quality" or "type_objectid_hash"
        val parts = requestParam.split("_")
        if (parts.size < 3 || parts.size > 4 || parts[3].toIntOrNull() != null) {
            throw IllegalArgumentException()
        }
        return CacheObject(
            nodeId = parts[1],
            type = parts[0],
            hash = parts[2],
            quality = if (parts.size == 4) parts[3].toIntOrNull() else null,
        )
    }
}