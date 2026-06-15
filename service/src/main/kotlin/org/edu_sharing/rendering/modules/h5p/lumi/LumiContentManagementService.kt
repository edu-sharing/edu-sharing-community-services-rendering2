package org.edu_sharing.rendering.modules.h5p.lumi

import tools.jackson.databind.ObjectMapper
import org.edu_sharing.rendering.cacheCleaner.TrackingEntry
import org.edu_sharing.rendering.modules.h5p.H5pRenderModule
import org.edu_sharing.rendering.modules.h5p.lumi.dto.LumiBucketInfo
import org.edu_sharing.rendering.modules.h5p.lumi.dto.LumiNodeHashResponse
import org.edu_sharing.rendering.modules.h5p.lumi.dto.LumiNodeInfo
import org.edu_sharing.rendering.security.NodeSessionContextRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.annotation.Cacheable
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import org.springframework.web.util.UriComponentsBuilder

// TODO impl. lumi per repoId!!!
@Service
class LumiContentManagementService(
    private val lumiWebClient: WebClient,
    private val lumiCacheRepository: LumiCacheRepository,
    private val nodeSessionContextRepo: NodeSessionContextRepository,
    @param:Lazy private val module: H5pRenderModule,
    @param:Value("\${app.security.enabled}")
    private val securityEnabled: Boolean
) {
    fun getNodeInfo(contentId: String): LumiNodeInfo {
        return lumiCacheRepository.findById(contentId).orElseGet{ retrieveNodeInfo(contentId) }
    }

    fun getContentId(nodeId: String, hash: String): String? {
        return lumiCacheRepository.findByNodeIdAndHash(nodeId, hash)?.lumiId
    }

    fun getCspHeader(nodeId: String): String? {
        if (!securityEnabled) return "frame-ancestors *"
        val repoId = nodeSessionContextRepo.getNode(nodeId)?.ref?.repo
            ?: throw IllegalStateException("Corrupt session state: Missing node permission for node $nodeId")
        return module.getCspHeader(repoId)
    }

    private fun retrieveNodeInfo(contentId: String) : LumiNodeInfo {
        val response = lumiWebClient.get()
            .uri {
                val uri = UriComponentsBuilder.fromUri(it.build())
                    .path("edusharing/contentid/$contentId")
                    .build(true)
                    .toUri()
                uri
            }.retrieve()
            .bodyToMono<String>()
            .block()
        val nodeHash = ObjectMapper().readValue(response, LumiNodeHashResponse::class.java).nodeId
        val nodeId = nodeHash.substringBefore("_")
        val hash = nodeHash.substringAfter("_")
        // TODO validation?
        val cacheEntry = LumiNodeInfo(
            lumiId = contentId,
            nodeId = nodeId,
            hash = hash,
        )
        return setCache(cacheEntry)
    }

    fun setCache(nodeInfo: LumiNodeInfo) : LumiNodeInfo {
        return lumiCacheRepository.save(nodeInfo)
    }

    @Cacheable("contentBucket")
    fun getContentBucket(repoId: String): String {
        val response = lumiWebClient.get()
            .uri {
                val uri = UriComponentsBuilder.fromUri(it.build())
                    .path("/edusharing/buckets/")
                    .build(true)
                    .toUri()
                uri
            }.retrieve()
            .bodyToMono<String>()
            .block()
        return ObjectMapper().readValue(response, LumiBucketInfo::class.java).contentBucket
    }

    fun deleteContent(trackingEntry: TrackingEntry) {
        lumiWebClient.delete()
            .uri {
                UriComponentsBuilder.fromUri(it.build())
                    .path("/edusharing/${trackingEntry.nodeId}_${trackingEntry.hash}")
                    .build(true)
                    .toUri()
            }.retrieve()
            .bodyToMono<Void>()
            .block()
        lumiCacheRepository.deleteByNodeId(trackingEntry.nodeId)
    }
}
