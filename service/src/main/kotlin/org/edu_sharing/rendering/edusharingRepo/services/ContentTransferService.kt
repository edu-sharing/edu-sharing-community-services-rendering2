package org.edu_sharing.rendering.edusharingRepo.services

import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.edusharingRepo.EncryptionService
import org.edu_sharing.rendering.utils.FluxInputStream
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ResourceLoader
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.bodyToFlux
import org.springframework.web.util.UriComponentsBuilder
import reactor.core.publisher.Flux
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
    @Value($$"${app.appId}")
    lateinit var appId: String

    companion object {
        private const val TEST_ID_PREFIX = "TEST_"
        private val log = LoggerFactory.getLogger(ContentTransferService::class.java)
    }

    fun getAsInputStream(cacheObject: CacheObject): InputStream {
        if(cacheObject.nodeId.startsWith(TEST_ID_PREFIX)) {
            val resourceName = cacheObject.nodeId.substring(TEST_ID_PREFIX.length)
            val resource = resourceLoader.getResource("classpath:$resourceName")
            cacheObject.size = resource.contentLength()
            log.debug("Opening test resource stream for nodeId=${cacheObject.nodeId}, size=${cacheObject.size}")
            return resource.inputStream
        }

        log.debug("Fetching content stream for repoId=${cacheObject.repoId}, nodeId=${cacheObject.nodeId}, version=${cacheObject.version}")
        val timeStamp = System.currentTimeMillis()
        val sigData = cacheObject.nodeId + timeStamp
        val signed = encryptionService.sign(sigData, cacheObject.repoId)
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
                    .queryParam("signedAlg", encryptionService.getSigningAlg(cacheObject.repoId))
                    .queryParam("version", cacheObject.version ?: "")
                    .build(true)
                    .toUri()
                uri
            }
            .retrieve()
            .bodyToFlux<org.springframework.core.io.buffer.DataBuffer>()
            .handle { buffer, sink ->
                if (buffer.readableByteCount() == 0) {
                    // `handle` transfers ownership of the buffer: one we neither emit nor release
                    // leaks its pooled Netty chunk (see S3StorageService.putObjectStreaming).
                    DataBufferUtils.release(buffer)
                    sink.error(Exception("Empty response received for ${cacheObject.repoId}/${cacheObject.nodeId}"))
                } else {
                    sink.next(buffer)
                }
            }
            .switchIfEmpty(
                Flux.error(
                    Exception("Empty response received for ${cacheObject.repoId}/${cacheObject.nodeId}")
                )
            )

        return FluxInputStream.toInputStream(returnedData)
    }
}
