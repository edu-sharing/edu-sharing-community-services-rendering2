package org.edu_sharing.rendering.modules.h5p.lumi

import com.fasterxml.jackson.databind.ObjectMapper
import org.edu_sharing.rendering.cacheCleaner.TrackingEntry
import org.edu_sharing.rendering.modules.h5p.lumi.dto.LumiBucketInfo
import org.edu_sharing.rendering.modules.h5p.lumi.dto.LumiNodeHashResponse
import org.edu_sharing.rendering.modules.h5p.lumi.dto.LumiNodeInfo
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.util.UriComponentsBuilder

@Service
class LumiContentManagementService(
    private val lumiWebClient: WebClient,
    private val lumiCacheRepository: LumiCacheRepository,
) {
    companion object {
        private var contentBucket: String = ""
    }

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
                    .path("edusharing/contentid/$contentId")
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

    fun getContentBucket(): String {
        if (contentBucket.isNotBlank()) {
            return contentBucket
        }
        val response = lumiWebClient.get()
            .uri {
                val uri = UriComponentsBuilder.fromUri(it.build())
                    .path("/edusharing/buckets/")
                    .build(true)
                    .toUri()
                uri
            }.retrieve()
            .bodyToMono(String::class.java)
            .block()
        val contentBucket = ObjectMapper().readValue(response, LumiBucketInfo::class.java).contentBucket
        return contentBucket
    }

    fun deleteContent(trackingEntry: TrackingEntry) {
        lumiWebClient.delete()
            .uri {
                UriComponentsBuilder.fromUri(it.build())
                    .path("/edusharing/${trackingEntry.nodeId}_${trackingEntry.hash}")
                    .build(true)
                    .toUri()
            }.retrieve()
            .bodyToMono(Void::class.java)
            .block()
    }
}
