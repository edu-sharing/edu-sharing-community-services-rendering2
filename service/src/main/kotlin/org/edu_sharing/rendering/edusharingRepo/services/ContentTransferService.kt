package org.edu_sharing.rendering.edusharingRepo.services

import org.edu_sharing.rendering.core.dto.CacheObject
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ResourceLoader
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.util.UriComponentsBuilder
import java.io.InputStream
import java.net.URLEncoder
import java.security.Signature
import java.util.*



@Service
class ContentTransferService(
    private val privatePublicKeyService: PrivatePublicKeyService,
    private val eduSharingWebClient: WebClient,
    @Qualifier("webApplicationContext") private val resourceLoader: ResourceLoader
) {
    @Value("\${app.appId}")
    lateinit var appId: String

    companion object {
        private const val TEST_ID_PREFIX = "TEST_"
    }

    fun getAsInputStream(cacheObject: CacheObject): InputStream {
        if(cacheObject.nodeId.startsWith(TEST_ID_PREFIX)) {
            val resourceName = cacheObject.nodeId.substring(TEST_ID_PREFIX.length)
            return resourceLoader.getResource("classpath:$resourceName").inputStream
        }

        val timeStamp = System.currentTimeMillis()
        val sigData = cacheObject.nodeId + timeStamp
        val privateKey = privatePublicKeyService.getPrivateKey()
        val dsa = Signature.getInstance("SHA1withRSA")
        dsa.initSign(privateKey)
        dsa.update(sigData.toByteArray())
        val signed = dsa.sign()
        val returnedData = eduSharingWebClient.get()
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
            .bodyToMono(ByteArray::class.java)
            .block()
        if (returnedData == null) {
            throw Exception("Empty data returned")
        }

        return returnedData.inputStream()
    }
}
