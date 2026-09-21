package org.edu_sharing.rendering.modules.h5p

import org.edu_sharing.rendering.cacheCleaner.TrackingService
import org.edu_sharing.rendering.core.annotation.ConditionalOnConverter
import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.edusharingRepo.services.ContentTransferService
import org.edu_sharing.rendering.modules.h5p.lumi.LumiContentManagementService
import org.edu_sharing.rendering.modules.h5p.lumi.dto.LumiContentResponse
import org.edu_sharing.rendering.storage.StorageService
import org.slf4j.LoggerFactory
import org.springframework.core.io.FileSystemResource
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import org.springframework.web.util.UriComponentsBuilder
import tools.jackson.databind.ObjectMapper
import java.io.File
import java.time.Duration

@Service
@ConditionalOnConverter
class H5pUploadService(
    private val contentTransferService: ContentTransferService,
    private val lumiWebClient: WebClient,
    private val lumiContentManagementService: LumiContentManagementService,
    private val trackingService: TrackingService,
    private val storageService: StorageService,
    private val module: H5pRenderModule,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(H5pUploadService::class.java)

    fun getContentId(cacheObject: CacheObject): String {
        log.debug("Resolving H5P content id for nodeId={}, hash={}", cacheObject.nodeId, cacheObject.hash)
        val lumiId = getLumiId(cacheObject)
        log.debug("H5P content id resolved: lumiId={} for nodeId={}", lumiId, cacheObject.nodeId)
        return lumiId
    }

    private fun getLumiId(cacheObject: CacheObject): String {
        val cachedLumiId = lumiContentManagementService.getContentId(
            nodeId = cacheObject.nodeId,
            hash = cacheObject.hash,
            timeout = getTimeout(cacheObject.repoId)
        )
        if (cachedLumiId != null) {
            trackingService.trackCacheObject(cacheObject, lumiContentManagementService.getContentBucket(cacheObject.repoId))
            return cachedLumiId
        }
        log.info("Not yet cached. Uploading H5P-Package.")
        val uploaded = uploadPackage(cacheObject)
        val lumiId = uploaded.contentId
        val size = storageService.getDirectorySize(lumiContentManagementService.getContentBucket(cacheObject.repoId), lumiId)
        // Tracking libraryBytes is what lets the CacheCleaner free the library volume by deleting
        // this package again. A current lumi always reports it (0 while it runs the shared global
        // library storage); a lumi older than the per-package cache omits it, which is passed on as
        // null so an already-known size is left alone rather than reset to 0.
        trackingService.trackCacheObject(
            cacheObject,
            lumiContentManagementService.getContentBucket(cacheObject.repoId),
            size,
            uploaded.libraryBytes
        )
        return lumiId
    }

    private fun uploadPackage(cacheObject: CacheObject): LumiContentResponse {
        log.debug("Uploading H5P package to Lumi for nodeId={}, hash={}", cacheObject.nodeId, cacheObject.hash)
        val originalFile = File.createTempFile(
            "${cacheObject.nodeId.substringBefore(".")}_${cacheObject.hash}_",
            ".h5p"
        )
        try {
            contentTransferService.getAsInputStream(cacheObject).use { inputStream ->
                originalFile.outputStream().use { outputStream -> inputStream.copyTo(outputStream) }
            }
            val builder = MultipartBodyBuilder()
            builder.part("file", FileSystemResource(originalFile))
            builder.part("nodeId", cacheObject.nodeId + "_" + cacheObject.hash)
            val response = lumiWebClient
                .post()
                .uri {
                    val uri = UriComponentsBuilder.fromUri(it.build())
                        .path("/edusharing")
                        .build(true)
                        .toUri()
                    uri
                }.contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .retrieve()
                .bodyToMono<String>()
                .timeout(getTimeout(cacheObject.repoId))
                .block()
            val uploaded = objectMapper.readValue(response, LumiContentResponse::class.java)
            log.debug(
                "H5P package upload complete: lumiContentId={} for nodeId={}, libraryBytes={}",
                uploaded.contentId, cacheObject.nodeId, uploaded.libraryBytes
            )
            return uploaded
        } finally {
            originalFile.delete()
        }
    }

    private fun getTimeout(repoId: String): Duration {
        val credentials = module.getCredentials(repoId)
        val timeout = credentials["timeout"]?.toLongOrNull() ?: 300L
        return Duration.ofSeconds(timeout)
    }
}
