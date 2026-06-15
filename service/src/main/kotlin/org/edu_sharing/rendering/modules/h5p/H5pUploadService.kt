package org.edu_sharing.rendering.modules.h5p

import tools.jackson.databind.ObjectMapper
import org.edu_sharing.rendering.cacheCleaner.TrackingService
import org.edu_sharing.rendering.core.annotation.ConditionalOnConverter
import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.edusharingRepo.services.ContentTransferService
import org.edu_sharing.rendering.modules.h5p.lumi.LumiContentManagementService
import org.edu_sharing.rendering.modules.h5p.lumi.dto.LumiContentResponse
import org.edu_sharing.rendering.modules.h5p.lumi.dto.LumiNodeInfo
import org.edu_sharing.rendering.storage.StorageService
import org.slf4j.LoggerFactory
import org.springframework.core.io.FileSystemResource
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToMono
import org.springframework.web.util.UriComponentsBuilder
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
    private val module: H5pRenderModule
) {
    private val log = LoggerFactory.getLogger(H5pUploadService::class.java)

    fun getContentId(cacheObject: CacheObject): String {
        val lumiId = getLumiId(cacheObject)
        val cacheEntry = LumiNodeInfo(
            lumiId = lumiId,
            nodeId = cacheObject.nodeId,
            hash = cacheObject.hash
        )
        lumiContentManagementService.setCache(cacheEntry)
        return lumiId
    }

    private fun getLumiId(cacheObject: CacheObject): String {
        try {
            val lumiId = getCachedContentId(cacheObject.nodeId, cacheObject.hash, cacheObject.repoId)
            trackingService.trackCacheObject(cacheObject, lumiContentManagementService.getContentBucket(cacheObject.repoId))
            return lumiId
        } catch (exception: WebClientResponseException) {
            if (exception.statusCode.isSameCodeAs(HttpStatus.NOT_FOUND)) {
                log.info("Not yet cached. Uploading H5P-Package.")
            } else {
                throw exception
            }
        }
        val lumiId = uploadPackage(cacheObject)
        val size = storageService.getDirectorySize(lumiContentManagementService.getContentBucket(cacheObject.repoId), lumiId)
        trackingService.trackCacheObject(cacheObject, lumiContentManagementService.getContentBucket(cacheObject.repoId), size)
        return lumiId
    }

    private fun getCachedContentId(nodeId: String, hash: String, repoId: String): String {
        val response = lumiWebClient.get()
            .uri {
                val uri = UriComponentsBuilder.fromUri(it.build())
                    .path("/edusharing/nodeid/${nodeId}_$hash")
                    .build(true)
                    .toUri()
                uri
            }.retrieve()
            .bodyToMono<String>()
            .timeout(getTimeout(repoId))
            .block()
        return ObjectMapper().readValue(response, LumiContentResponse::class.java).contentId
    }

    private fun uploadPackage(cacheObject: CacheObject): String {
        val inputStream = contentTransferService.getAsInputStream(cacheObject)
        val originalFile = File.createTempFile(
            "${cacheObject.nodeId.substringBefore(".")}_${cacheObject.hash}_",
            ".h5p"
        )
        inputStream.use {
            originalFile.outputStream().use { outputStream -> inputStream.copyTo(outputStream) }
        }
        val builder = MultipartBodyBuilder()
        builder.part("file", FileSystemResource(originalFile))
        builder.part("nodeId", cacheObject.nodeId + "_" + cacheObject.hash)
        try {
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
            return ObjectMapper().readValue(response, LumiContentResponse::class.java).contentId
        } catch (exception: Exception) {
            throw exception
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
