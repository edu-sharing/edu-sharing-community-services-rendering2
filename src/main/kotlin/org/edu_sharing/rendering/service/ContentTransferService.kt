package org.edu_sharing.rendering.service

import org.edu_sharing.rendering.dto.CacheObject
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
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

@Service
class ContentTransferService(
    private val privatePublicKeyService: PrivatePublicKeyService,
    private val eduSharingWebClient: WebClient
) {
    @Value("\${app.appId}")
    lateinit var appId: String

    private val log = LoggerFactory.getLogger(javaClass)

    fun getAsInputStream(cacheObject: CacheObject): InputStream {
        val outputStreamPipe = PipedOutputStream()
        val inputStreamPipe = PipedInputStream(outputStreamPipe)
        val timeStamp = System.currentTimeMillis()
        val sigData = cacheObject.nodeId + timeStamp
        //val cipher = Cipher.getInstance("RSA")
        val privateKey = privatePublicKeyService.getPrivateKey()
        //cipher.init(Cipher.ENCRYPT_MODE, privateKey)
        //val encryptedSigData = Base64.getEncoder().encode(cipher.doFinal(sigData.toByteArray())).decodeToString()
        //val encoder = URLEncoder()
        val dsa = Signature.getInstance("SHA1withRSA")
        dsa.initSign(privateKey)
        dsa.update(sigData.toByteArray())
        val signed = dsa.sign()
        log.info(URLEncoder.encode(Base64.getEncoder().encodeToString(signed)))
        val body = eduSharingWebClient.get()
            .uri {
                val uri = UriComponentsBuilder.fromUri(it.build())
                    .path("/content")
                    .queryParam("repId", cacheObject.repoId ?: "")
                    .queryParam("appId", appId)
                    .queryParam("nodeId", cacheObject.nodeId)
                    .queryParam("timeStamp", timeStamp)
                    .queryParam("authToken", URLEncoder.encode(Base64.getEncoder().encodeToString(signed)))
                    .queryParam("version", cacheObject.version ?: "")
                    .build(true)
                    .toUri()

                log.info("Auth: {}",Base64.getEncoder().encodeToString(signed))
                log.info("url: {}", uri)
                uri
            }.exchangeToFlux { it.body(BodyExtractors.toDataBuffers()) }
        DataBufferUtils.write(body, outputStreamPipe)
            .doOnError { log.error("something went wrong: {}", it.message, it); throw it }
            .subscribe(DataBufferUtils.releaseConsumer())

        return inputStreamPipe
    }
}