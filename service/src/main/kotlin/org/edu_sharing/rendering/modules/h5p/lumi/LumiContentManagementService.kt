package org.edu_sharing.rendering.modules.h5p.lumi

import com.fasterxml.jackson.databind.ObjectMapper
import org.edu_sharing.rendering.cacheCleaner.TrackingEntry
import org.edu_sharing.rendering.cacheCleaner.TrackingEntryRepository
import org.edu_sharing.rendering.cacheCleaner.TrackingService
import org.edu_sharing.rendering.modules.h5p.lumi.dto.LumiBucketInfo
import org.edu_sharing.rendering.modules.h5p.lumi.dto.LumiNodeHashResponse
import org.edu_sharing.rendering.modules.h5p.lumi.dto.LumiNodeInfo
import org.edu_sharing.rendering.storage.StorageService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.util.UriComponentsBuilder

@Service
class LumiContentManagementService(
    private val lumiWebClient: WebClient,
    private val lumiCacheRepository: LumiCacheRepository,
    private val trackingService: TrackingService,
    private val trackingEntryRepository: TrackingEntryRepository,
    private val storageService: StorageService
) {
    companion object {
        private var contentBucket: String = ""
    }

    private val log = LoggerFactory.getLogger(LumiContentManagementService::class.java)

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
                    .path("contentBucketInfo/edusharing/contentid/$contentId")
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

    fun freeContentBucket(targetSize: Long) {
        val iterator = trackingService.getTrackedObjectsByBucket(getContentBucket())
        do {
            for (entry in iterator.getNext()) {
                try {
                    deleteContent(entry)
                    trackingEntryRepository.delete(entry)
                } catch (exception: WebClientResponseException) {
                    if (exception.statusCode == HttpStatus.NOT_FOUND) {
                        log.warn("Orphaned lumi tracking entry detected. It will be deleted.")
                        trackingEntryRepository.delete(entry)
                    } else {
                        log.error("Lumi object cannot be removed: ${exception.statusCode}")
                    }
                } catch (exception: Exception) {
                    log.error("Lumi object cannot be removed: ${exception.message}")
                }
            }

        } while (iterator.hasNext() && getContentBucketSize() > targetSize)
    }

    private fun deleteContent(trackingEntry: TrackingEntry) {
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

    private fun getContentBucketSize(): Long {
        val bucketInfoList = storageService.getStorageInfo()
        val contentBucketInfo = bucketInfoList.first {it.location == getContentBucket()}
        return contentBucketInfo.size
    }
}