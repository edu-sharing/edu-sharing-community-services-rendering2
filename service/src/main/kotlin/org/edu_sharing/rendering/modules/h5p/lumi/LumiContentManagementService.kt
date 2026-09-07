package org.edu_sharing.rendering.modules.h5p.lumi

import tools.jackson.databind.ObjectMapper
import org.edu_sharing.rendering.cacheCleaner.TrackingEntry
import org.edu_sharing.rendering.core.exception.ResourceNotFoundException
import org.edu_sharing.rendering.modules.h5p.H5pRenderModule
import org.edu_sharing.rendering.modules.h5p.lumi.dto.LumiBucketInfo
import org.edu_sharing.rendering.modules.h5p.lumi.dto.LumiContentResponse
import org.edu_sharing.rendering.modules.h5p.lumi.dto.LumiNodeHashResponse
import org.edu_sharing.rendering.modules.h5p.lumi.dto.LumiNodeInfo
import org.edu_sharing.rendering.security.NodeSessionContextRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToMono
import org.springframework.web.util.UriComponentsBuilder
import java.time.Duration
import java.time.Instant
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

// TODO impl. lumi per repoId!!!
@Service
class LumiContentManagementService(
    private val lumiWebClient: WebClient,
    private val nodeSessionContextRepo: NodeSessionContextRepository,
    private val objectMapper: ObjectMapper,
    private val module: H5pRenderModule,
    @param:Value($$"${app.security.enabled}")
    private val securityEnabled: Boolean,
    @param:Value($$"${app.lumi.bucketInfo.cacheTtl:PT1M}")
    private val bucketInfoCacheTtl: Duration
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        /** Both id lookups are a single indexed Mongo query inside Lumi — never wait long for them. */
        private val LOOKUP_TIMEOUT: Duration = Duration.ofSeconds(10)
        private const val NODE_INFO_CACHE_SIZE = 1024
    }

    /**
     * `getBucketInfo` is polled by the admin dashboard (storage usage, every poll tick) and by the
     * daily CacheCleaner — both need the bucket name/quota, but `@Cacheable` is a no-op in this module
     * (no `@EnableCaching`/`spring-boot-starter-cache`, see `RepositoryRegistrationStorageService` for
     * the same gap). Without a real cache here, every dashboard poll would add an extra HTTP round
     * trip to lumi. Bucket name/quota change essentially never at runtime, so a short, hand-rolled TTL
     * cache is enough — this is deliberately scoped to just this one call, not a general cache fix.
     */
    private val bucketInfoCache = ConcurrentHashMap<String, Pair<LumiBucketInfo, Instant>>()

    /**
     * contentId -> node mapping, resolved on every proxied request — including every asset below
     * `/content/{contentId}/` — so it is cached locally to keep one page render from fanning out
     * into an extra Lumi round trip per asset.
     *
     * Lumi writes this mapping once when the package is uploaded and never updates it, so entries
     * cannot go wrong, only away: deletion is the sole invalidation and is best effort per instance.
     * A surviving entry after a delete is harmless — it still carries the correct nodeId for the
     * permission check, and the proxied request behind it runs against Lumi and 404s there.
     */
    private val nodeInfoCache: MutableMap<String, LumiNodeInfo> = Collections.synchronizedMap(
        object : LinkedHashMap<String, LumiNodeInfo>(NODE_INFO_CACHE_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LumiNodeInfo>) =
                size > NODE_INFO_CACHE_SIZE
        }
    )

    fun getNodeInfo(contentId: String): LumiNodeInfo {
        nodeInfoCache[contentId]?.let {
            log.debug("Lumi node info cache hit for contentId={}", contentId)
            return it
        }
        return retrieveNodeInfo(contentId).also { nodeInfoCache[contentId] = it }
    }

    /**
     * Presence check against Lumi: the content id of an already rendered node revision, or `null`
     * if Lumi holds no rendering for it. Errors other than "not found" are propagated — a caller
     * that must not fail on an unreachable Lumi has to treat them as a miss itself.
     */
    fun getContentId(nodeId: String, hash: String, timeout: Duration = LOOKUP_TIMEOUT): String? {
        log.debug("Looking up Lumi content id for nodeId={}, hash={}", nodeId, hash)
        val response = try {
            lumiWebClient.get()
                .uri {
                    UriComponentsBuilder.fromUri(it.build())
                        .path("/edusharing/nodeid/${nodeId}_$hash")
                        .build(true)
                        .toUri()
                }.retrieve()
                .bodyToMono<String>()
                .timeout(timeout)
                .block()
        } catch (exception: WebClientResponseException) {
            if (exception.statusCode.isSameCodeAs(HttpStatus.NOT_FOUND)) {
                log.debug("Lumi holds no rendering for nodeId={}, hash={}", nodeId, hash)
                return null
            }
            throw exception
        }
        return objectMapper.readValue(response, LumiContentResponse::class.java).contentId
    }

    fun getCspHeader(nodeId: String): String? {
        if (!securityEnabled) return "frame-ancestors *"
        val repoId = nodeSessionContextRepo.getNode(nodeId)?.ref?.repo
            ?: throw IllegalStateException("Corrupt session state: Missing node permission for node $nodeId")
        return module.getCspHeader(repoId)
    }

    private fun retrieveNodeInfo(contentId: String): LumiNodeInfo {
        log.debug("Retrieving Lumi node info for contentId={}", contentId)
        val response = try {
            lumiWebClient.get()
                .uri {
                    UriComponentsBuilder.fromUri(it.build())
                        .path("/edusharing/contentid/$contentId")
                        .build(true)
                        .toUri()
                }.retrieve()
                .bodyToMono<String>()
                .timeout(LOOKUP_TIMEOUT)
                .block()
        } catch (exception: WebClientResponseException) {
            if (exception.statusCode.isSameCodeAs(HttpStatus.NOT_FOUND)) {
                throw ResourceNotFoundException("Lumi holds no content with id $contentId")
            }
            throw exception
        }
        val nodeHash = objectMapper.readValue(response, LumiNodeHashResponse::class.java).nodeId
        return LumiNodeInfo(
            lumiId = contentId,
            nodeId = nodeHash.substringBefore("_"),
            hash = nodeHash.substringAfter("_"),
        )
    }

    fun getContentBucket(repoId: String): String = getBucketInfo(repoId).contentBucket

    /**
     * Name + quota in one call. Prefer this over `getContentBucket(repoId)` followed by a separate
     * quota lookup whenever a caller needs both — one call is guaranteed to pair a consistent
     * name/quota from the same fetch, whereas two calls could (in the rare case a TTL refresh lands
     * between them) return values from two different fetches.
     */
    fun getContentBucketInfo(repoId: String): LumiBucketInfo = getBucketInfo(repoId)

    private fun getBucketInfo(repoId: String): LumiBucketInfo {
        bucketInfoCache[repoId]?.let { (info, fetchedAt) ->
            if (Duration.between(fetchedAt, Instant.now()) < bucketInfoCacheTtl) return info
        }
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
        val info = objectMapper.readValue(response, LumiBucketInfo::class.java)
        bucketInfoCache[repoId] = info to Instant.now()
        return info
    }

    fun deleteContent(trackingEntry: TrackingEntry) {
        log.debug("Deleting Lumi content for nodeId={}, hash={}", trackingEntry.nodeId, trackingEntry.hash)
        lumiWebClient.delete()
            .uri {
                UriComponentsBuilder.fromUri(it.build())
                    .path("/edusharing/${trackingEntry.nodeId}_${trackingEntry.hash}")
                    .build(true)
                    .toUri()
            }.retrieve()
            .bodyToMono<Void>()
            .block()
        nodeInfoCache.values.removeIf { it.nodeId == trackingEntry.nodeId && it.hash == trackingEntry.hash }
    }
}
