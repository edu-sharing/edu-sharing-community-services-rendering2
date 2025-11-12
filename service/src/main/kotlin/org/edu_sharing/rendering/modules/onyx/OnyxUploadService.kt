package org.edu_sharing.rendering.modules.onyx

import org.edu_sharing.rendering.core.annotation.ConditionalOnConverter
import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.edusharingRepo.services.ContentTransferService
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
    private val contentTransferService: ContentTransferService
) {
    fun uploadTest(cacheObject: CacheObject): String {
        val config = module.getConfig(cacheObject.repoId)
        val inputStream = contentTransferService.getAsInputStream(cacheObject)
        val originalFile = File.createTempFile(
            "${cacheObject.nodeId.substringBefore(".")}_${cacheObject.hash}",
            "zip"
        )
        val webClient = WebClient
            .builder()
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

            webClient
                .post()
                .uri("/run")
                .contentType(org.springframework.http.MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .retrieve()
                .onStatus({ t -> t.isError }, { it.createException() })
                .bodyToMono(Void::class.java)
                .block()

            return "${config["onyxrunurl"] ?: ""}?id=${cacheObject.nodeId}_${cacheObject.hash}"
        } catch (exception: Exception) {
            throw exception
        } finally {
            originalFile.delete()
        }
    }
}
