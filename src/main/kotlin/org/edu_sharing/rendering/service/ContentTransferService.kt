package org.edu_sharing.rendering.service

import org.edu_sharing.rendering.dto.CacheObject
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ResourceLoader
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.BodyExtractors
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.util.UriComponentsBuilder
import java.io.InputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.net.URLEncoder
import java.security.Signature
import java.util.*

private const val TEST_ID_PREFIX = "TEST_"

@Service
class ContentTransferService(
    private val privatePublicKeyService: PrivatePublicKeyService,
    private val eduSharingWebClient: WebClient,
    @Qualifier("webApplicationContext") private val resourceLoader: ResourceLoader
) {
    @Value("\${app.appId}")
    lateinit var appId: String

    private val log = LoggerFactory.getLogger(javaClass)

    fun getAsInputStream(cacheObject: CacheObject): InputStream {
        if(cacheObject.nodeId.startsWith(TEST_ID_PREFIX)) {
            val resourceName = cacheObject.nodeId.substring(TEST_ID_PREFIX.length)
            return resourceLoader.getResource("classpath:$resourceName").inputStream
        }
        val outputStreamPipe = PipedOutputStream()
        val inputStreamPipe = PipedInputStream(outputStreamPipe)
        val timeStamp = System.currentTimeMillis()
        val sigData = cacheObject.nodeId + timeStamp
        val privateKey = privatePublicKeyService.getPrivateKey()
        val dsa = Signature.getInstance("SHA1withRSA")
        dsa.initSign(privateKey)
        dsa.update(sigData.toByteArray())
        val signed = dsa.sign()
        val body = eduSharingWebClient.get()
            .uri {
                val uri = UriComponentsBuilder.fromUri(it.build())
                    .path("/content")
                    .queryParam("repId", cacheObject.repoId ?: "")
                    .queryParam("appId", appId)
                    .queryParam("nodeId", cacheObject.nodeId)
                    .queryParam("timeStamp", timeStamp)
                    .queryParam("authToken", URLEncoder.encode(Base64.getEncoder().encodeToString(signed), Charsets.UTF_8))
                    .queryParam("version", cacheObject.version ?: "")
                    .build(true)
                    .toUri()
                uri
            }.exchangeToFlux { it.body(BodyExtractors.toDataBuffers()) }

        DataBufferUtils.write(body, outputStreamPipe)
            .doOnError { log.error("something went wrong: {}", it.message, it); throw it }
            .subscribe(DataBufferUtils.releaseConsumer())

        return inputStreamPipe
    }
}