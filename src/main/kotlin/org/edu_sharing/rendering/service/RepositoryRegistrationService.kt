package org.edu_sharing.rendering.service

import org.edu_sharing.generated.repository.backend.services.rest.client.api.AdminV1Api
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.scheduler.Schedulers
import java.security.InvalidKeyException
import java.util.*

@Service
@ConditionalOnProperty(name = ["edu_sharing.registration.enabled"], havingValue = "true")
class RepositoryRegistrationService(
    private val privatePublicKeyService: PrivatePublicKeyService,
    private val adminV1Api: AdminV1Api,
    private val eduSharingWebClient: WebClient,
    @Value("\${app.public.url}") private var publicUrl: String,
    @Value("\${app.public.port}") private var port: String
) {
    fun updatePublicRepositoryKey() {
        val publicKey = eduSharingWebClient
            .get()
            .uri {
                it.path("/metadata")
                    .queryParam("format", "lms")
                    .queryParam("external", true)
                    .build()
            }.accept(MediaType.APPLICATION_XML)
            .retrieve()
            .bodyToMono(String::class.java)
            .publishOn(Schedulers.boundedElastic())
            .mapNotNull {
                val buffer = it.byteInputStream()
                val props = Properties()
                props.loadFromXML(buffer)
                props["public_key"].toString()
            }
            .block()
        if(publicKey.isNullOrBlank()) {
            throw InvalidKeyException("Received key is empty")
        }
        privatePublicKeyService.storeRepositoryKey(publicKey)
    }

    fun registerWithRepository() {
        adminV1Api.addApplication1("$publicUrl:$port/public/metadata")
        updatePublicRepositoryKey()
    }
}