package org.edu_sharing.rendering.modules.onyx

import org.edu_sharing.rendering.core.annotation.ConditionalOnConverter
import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.edusharingRepo.services.ContentTransferService
import org.slf4j.LoggerFactory
import org.springframework.core.io.FileSystemResource
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import java.io.File

@Service
@ConditionalOnConverter
class OnyxUploadService(
    private val module: OnyxRenderModule,
    private val contentTransferService: ContentTransferService,
    private val webClientBuilder: WebClient.Builder
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun uploadTest(cacheObject: CacheObject): String {
        log.debug("Downloading content for Onyx upload, nodeId ${cacheObject.nodeId}, hash ${cacheObject.hash}")
        val config = module.getCredentials(cacheObject.repoId)
        val inputStream = contentTransferService.getAsInputStream(cacheObject)
        val originalFile = File.createTempFile(
            "${cacheObject.nodeId.substringBefore(".")}_${cacheObject.hash}",
            "zip"
        )
        val webClient = webClientBuilder
            .clone()
            .baseUrl(config["onyxresturl"] ?: "")
            .build()
        try {
            inputStream.use {
                originalFile.outputStream().use { outputStream -> inputStream.copyTo(outputStream) }
            }
            val builder = MultipartBodyBuilder()
            builder.part("contentPackage", FileSystemResource(originalFile))
            builder.part("uniqueId", "${cacheObject.nodeId}_${cacheObject.hash}")
            builder.part("language", "de")
            builder.part("serviceName", config["returnservice"] ?: "")
            builder.part("allowShowSolution", "true")
            builder.part("templateId", "onyxdefault")

            log.debug("Posting content package to Onyx REST endpoint ${config["onyxresturl"]}/run for nodeId ${cacheObject.nodeId}")
            webClient
                .post()
                .uri("/run")
                .contentType(org.springframework.http.MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .retrieve()
                .onStatus({ t -> t.isError }, { it.createException() })
                .bodyToMono(Void::class.java)
                .block()

            val runUrl = "${config["onyxrunurl"] ?: ""}?id=${cacheObject.nodeId}_${cacheObject.hash}"
            log.debug("Onyx upload complete, run URL: $runUrl")
            return runUrl
        } catch (exception: Exception) {
            throw exception
        } finally {
            originalFile.delete()
        }
    }
}
