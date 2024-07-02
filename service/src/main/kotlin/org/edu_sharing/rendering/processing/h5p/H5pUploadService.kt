package org.edu_sharing.rendering.processing.h5p

import com.fasterxml.jackson.databind.ObjectMapper
import org.edu_sharing.rendering.config.annotation.ConditionalOnH5p
import org.edu_sharing.rendering.dto.CacheObject
import org.edu_sharing.rendering.processing.moodle.MoodleReceiver
import org.edu_sharing.rendering.service.ContentTransferService
import org.slf4j.LoggerFactory
import org.springframework.core.io.FileSystemResource
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.util.UriComponentsBuilder
import java.io.File
import java.nio.file.Files

@Service
@ConditionalOnH5p
class H5pUploadService(
    private val contentTransferService: ContentTransferService,
    private val lumiWebClient: WebClient,
) {
    private val log = LoggerFactory.getLogger(MoodleReceiver::class.java)

    fun getContentId(cacheObject: CacheObject): String {
        try {
            return getCachedContentId(cacheObject.nodeId, cacheObject.hash)
        } catch (exception: WebClientResponseException) {
            if (exception.statusCode.isSameCodeAs(HttpStatus.NOT_FOUND)) {
                log.info("Not yet cached. Uploading H5P-Package.")
            } else {
                throw exception
            }
        }
        return uploadPackage(cacheObject)
    }

    private fun getCachedContentId(nodeId: String, hash: String): String {
        val response = lumiWebClient.get()
            .uri {
                val uri = UriComponentsBuilder.fromUri(it.build())
                    .path("/edusharing/${nodeId}_$hash")
                    .build(true)
                    .toUri()
                uri
            }.retrieve()
            .bodyToMono(String::class.java)
            .block()
        return ObjectMapper().readValue(response, LumiResponse::class.java).contentId
    }

    private fun uploadPackage(cacheObject: CacheObject): String {
        val inputStream = contentTransferService.getAsInputStream(cacheObject)
        val originalFile = File("${cacheObject.nodeId}_${cacheObject.hash}.h5p")
        inputStream.use {
            Files.copy(inputStream, originalFile.toPath())
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
                .retrieve().bodyToMono(String::class.java).block()
            return ObjectMapper().readValue(response, LumiResponse::class.java).contentId
        } catch (exception: Exception) {
            throw exception
        } finally {
            originalFile.delete()
        }
    }
}