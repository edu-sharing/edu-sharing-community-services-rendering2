package org.edu_sharing.rendering.edusharingRepo.services

import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.edusharingRepo.EncryptionService
import org.edu_sharing.rendering.utils.FluxInputStream
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ResourceLoader
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.bodyToFlux
import org.springframework.web.util.UriComponentsBuilder
import java.io.InputStream
import java.net.URLEncoder
import java.util.*

@Service
class ContentTransferService(
    private val repoRegistrationService: RepositoryRegistrationService,
    private val encryptionService: EncryptionService,
    @param:Qualifier("webApplicationContext")
    private val resourceLoader: ResourceLoader
) {
    @Value("\${app.appId}")
    lateinit var appId: String

    companion object {
        private const val TEST_ID_PREFIX = "TEST_"
    }

    fun getAsInputStream(cacheObject: CacheObject): InputStream {
        if(cacheObject.nodeId.startsWith(TEST_ID_PREFIX)) {
            val resourceName = cacheObject.nodeId.substring(TEST_ID_PREFIX.length)
            val resource = resourceLoader.getResource("classpath:$resourceName")
            cacheObject.size = resource.contentLength()
            return resource.inputStream
        }

        val timeStamp = System.currentTimeMillis()
        val sigData = cacheObject.nodeId + timeStamp
        val signed = encryptionService.sign(sigData)
        val returnedData = repoRegistrationService.getWebClientByRepoId(cacheObject.repoId)
            .get()
            .uri {
                val uri = UriComponentsBuilder.fromUri(it.build())
                    .path("/content")
                    .queryParam("repId", cacheObject.repoId)
                    .queryParam("appId", appId)
                    .queryParam("nodeId", cacheObject.nodeId)
                    .queryParam("timeStamp", timeStamp)
                    .queryParam("authToken",
                        URLEncoder.encode(Base64.getEncoder().encodeToString(signed), Charsets.UTF_8)
                    )
                    .queryParam("version", cacheObject.version ?: "")
                    .build(true)
                    .toUri()
                uri
            }.retrieve()
            .bodyToFlux<org.springframework.core.io.buffer.DataBuffer>()

        return FluxInputStream.toInputStream(returnedData)
    }
}
