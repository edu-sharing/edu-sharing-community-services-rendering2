package org.edu_sharing.rendering.modules.onyx

import org.edu_sharing.rendering.utils.JobTempFiles
import org.edu_sharing.rendering.core.annotation.ConditionalOnConverter
import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.edusharingRepo.services.ContentTransferService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.io.FileSystemResource
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import java.io.File
import java.time.Duration

@Service
@ConditionalOnConverter
class OnyxUploadService(
    private val module: OnyxRenderModule,
    private val contentTransferService: ContentTransferService,
    @param:Qualifier("longRunningWebClientBuilder")
    private val webClientBuilder: WebClient.Builder
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private companion object {
        /** Hard bound for the whole upload call; the connector's response timeout only covers the wait for headers. */
        val UPLOAD_TIMEOUT: Duration = Duration.ofMinutes(10)
    }

    fun uploadTest(cacheObject: CacheObject): String {
        log.debug("Downloading content for Onyx upload, nodeId ${cacheObject.nodeId}, hash ${cacheObject.hash}")
        val config = module.getCredentials(cacheObject.repoId)
        val originalFile = JobTempFiles.createFile(
            "${cacheObject.nodeId.substringBefore(".")}_${cacheObject.hash}",
            "zip"
        )
        val webClient = webClientBuilder
            .clone()
            .baseUrl(config["onyxresturl"] ?: "")
            .build()
        try {
            // Opened inside the try, after the temp file exists: a failure between opening the content
            // stream and reaching `use` would orphan its pooled Netty buffers for the life of the JVM.
            contentTransferService.getAsInputStream(cacheObject).use { inputStream ->
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
                .bodyToMono(Void::class.java)
                .timeout(UPLOAD_TIMEOUT)
                .block()

            val runUrl = "${config["onyxrunurl"] ?: ""}?id=${cacheObject.nodeId}_${cacheObject.hash}"
            log.debug("Onyx upload complete, run URL: $runUrl")
            return runUrl
        } finally {
            originalFile.delete()
        }
    }
}
