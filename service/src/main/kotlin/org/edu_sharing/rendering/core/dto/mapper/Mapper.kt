package org.edu_sharing.rendering.core.dto.mapper

import org.edu_sharing.generated.repository.backend.services.rest.client.model.Node
import org.edu_sharing.rendering.asset.dto.AssetLinkParams
import org.edu_sharing.rendering.cacheCleaner.TrackingEntry
import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.utils.storageNodeId
import org.springframework.stereotype.Component

@Component
class Mapper {
    fun nodeToCacheObject(node: Node): CacheObject {
        return CacheObject(
            nodeId = node.storageNodeId(),
            type = node.mediatype ?: "",
            hash = node.content?.hash ?: "nohash",
            size = if (node.size.isNullOrEmpty()) -1 else node.size!!.toLong(),
            mimeType = node.mimetype ?: "",
            version = node.content?.version ?: "",
            repoId = node.ref.repo
        )
    }

    fun nodeToRenderingJob(node: Node, module: String, isConversionType: Boolean = false): RenderingJob {
        return RenderingJob(
            esObjectId = node.storageNodeId(),
            esObjectType = node.mediatype ?: "",
            esHash = node.content?.hash ?: "nohash",
            mimeType = node.mimetype ?: "",
            repoId = node.ref.repo,
            nodeVersion = node.content?.version ?: "",
            size = if (node.size.isNullOrEmpty()) null else node.size?.toLong(),
            module = module,
            conversionType = isConversionType,
            externalUrl = node.properties?.getOrDefault("ccm:wwwurl", mutableListOf(""))[0]
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

    fun renderingJobToCacheObject(renderingJob: RenderingJob): CacheObject {
        return CacheObject(
            nodeId = renderingJob.esObjectId,
            type = renderingJob.esObjectType,
            hash = renderingJob.esHash,
            mimeType = renderingJob.mimeType,
            size = renderingJob.size ?: -1,
            version = renderingJob.nodeVersion,
            repoId = renderingJob.repoId,
            externalUrl = renderingJob.externalUrl
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

    fun trackingEntryToCacheObject(trackingEntry: TrackingEntry): CacheObject {
        return CacheObject(
            repoId = trackingEntry.repoId,
            nodeId = trackingEntry.nodeId,
            hash = trackingEntry.hash,
            type = trackingEntry.type
        )
    }
}
