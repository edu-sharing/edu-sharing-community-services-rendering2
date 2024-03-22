package org.edu_sharing.rendering.config

import org.apache.commons.codec.binary.Base64
import org.edu_sharing.generated.repository.backend.services.rest.client.api.AdminV1Api
import org.edu_sharing.rendering.entity.AppConfig
import org.edu_sharing.rendering.repository.mongo.AppConfigRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.security.KeyPairGenerator

@Component
class RegistrationRunner(
    private val appConfigRepository: AppConfigRepository,
    private val adminV1Api: AdminV1Api
): ApplicationRunner {

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
        return AppConfig(
            appId = "Renderer2",
            appCaption = "I am the new renderer",
            trustedClient = true,
            host = publicUrl,
            port = port.toInt(),
            scheme = "http"
        )
    }

    private fun generateKeys(appConfig: AppConfig) {
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048)
        val keyPair = generator.generateKeyPair()
        appConfig.privateKey = String(Base64().encode(keyPair.private.encoded))
        appConfig.publicKey = "-----BEGIN PUBLIC KEY-----\n" + String(Base64().encode(keyPair.public.encoded)) + "-----END PUBLIC KEY-----"
    }

    private fun register(appConfig: AppConfig) {
        try {
            val result = adminV1Api.addApplication1("$publicUrl:$port/public/metadata")
            log.info("Registration completed: {}", result)
        } catch (e: Exception) {
            log.error(e.message)
        }

        // getMetadata
    }
}
