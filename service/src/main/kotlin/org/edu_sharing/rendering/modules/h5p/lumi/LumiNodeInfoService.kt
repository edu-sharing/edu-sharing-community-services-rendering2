package org.edu_sharing.rendering.modules.h5p.lumi

import com.fasterxml.jackson.databind.ObjectMapper
import org.edu_sharing.rendering.modules.h5p.lumi.dto.LumiNodeHashResponse
import org.edu_sharing.rendering.modules.h5p.lumi.dto.LumiNodeInfo

import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.util.UriComponentsBuilder

@Service
class LumiNodeInfoService(
    private val lumiWebClient: WebClient,
    private val lumiCacheRepository: LumiCacheRepository) {

    fun getNodeInfo(contentId: String): LumiNodeInfo {
        return lumiCacheRepository.findById(contentId).orElseGet{ retrieveNodeInfo(contentId) }
    }

    fun getContentId(nodeId: String, hash: String): String? {
        return lumiCacheRepository.findByNodeIdAndHash(nodeId, hash)?.lumiId
    }

    private fun retrieveNodeInfo(contentId: String) : LumiNodeInfo {
        val response = lumiWebClient.get()
            .uri {
                val uri = UriComponentsBuilder.fromUri(it.build())
                    .path("/edusharing/contentid/$contentId")
                    .build(true)
                    .toUri()
                uri
            }.retrieve()
            .bodyToMono(String::class.java)
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
}