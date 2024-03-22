package org.edu_sharing.rendering.config

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.commons.codec.binary.Base64
import org.edu_sharing.generated.repository.backend.services.rest.client.api.AdminV1Api
import org.edu_sharing.rendering.entity.AppConfig
import org.edu_sharing.rendering.repository.mongo.AppConfigRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.scheduler.Schedulers
import java.security.KeyPairGenerator
import java.util.*

@Component
class RegistrationRunner(
    private val appConfigRepository: AppConfigRepository,
    private val adminV1Api: AdminV1Api,
    private val eduSharingWebClient: WebClient
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    @Value("\${app.public.url}")
    lateinit var publicUrl: String

    @Value("\${app.public.port}")
    lateinit var port: String

    @Transactional
    override fun run(args: ApplicationArguments?) {
        val allEntries = appConfigRepository.findAll()
        val config = if (allEntries.size == 0) initValues() else allEntries[0]
        if (config.privateKey == null) {
            generateKeys(config)
        }
        if (config.repoPublicKey == null) {
            register(config)
        }
        appConfigRepository.save(config)
    }

    private fun initValues(): AppConfig {
        return AppConfig()
    }

    private fun generateKeys(appConfig: AppConfig) {
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048)
        val keyPair = generator.generateKeyPair()
        appConfig.privateKey = String(Base64().encode(keyPair.private.encoded))
        appConfig.publicKey =
            "-----BEGIN PUBLIC KEY-----\n" + String(Base64().encode(keyPair.public.encoded)) + "-----END PUBLIC KEY-----"
    }

    private fun register(appConfig: AppConfig) {
        try {
            val result = adminV1Api.addApplication1("$publicUrl:$port/public/metadata")

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

            if (publicKey != null) {
                appConfig.repoPublicKey = publicKey
                appConfigRepository.save(appConfig)
                log.info("Registration completed: {}", result)
            } else {
                log.warn("Registration uncompleted: repo response doesn't contains a public_key {}", result)
            }
        } catch (e: Exception) {
            log.error(e.message)
        }
    }
}
