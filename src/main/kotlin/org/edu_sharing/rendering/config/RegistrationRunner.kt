package org.edu_sharing.rendering.config

import org.apache.commons.lang3.RandomStringUtils
import org.edu_sharing.generated.repository.backend.services.rest.client.api.AdminV1Api
import org.edu_sharing.rendering.entity.AppConfig
import org.edu_sharing.rendering.repository.mongo.AppConfigRepository
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

    @Transactional
    override fun run(args: ApplicationArguments?) {
        /*
        val allEntries = appConfigRepository.findAll()
        val config = if (allEntries.size == 0) initValues() else allEntries[0]
        if (config.privateKey == null) {
            generateKeys(config)
        }
        if (config.repoPublicKey == null) {
            register(config)
        }
        appConfigRepository.save(config)

         */
    }

    private fun initValues(): AppConfig {
        return AppConfig(
            appId = "Renderer_" + RandomStringUtils.random(8),
            appCaption = "I am the new renderer",
            trustedClient = true,
            host = "localhost",
            port = 8080,
            scheme = "http"
        )
    }

    private fun generateKeys(appConfig: AppConfig) {
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(128)
        val keyPair = generator.generateKeyPair()
        appConfig.privateKey = keyPair.private.toString()
        appConfig.publicKey = keyPair.public.toString()
    }

    private fun register(appConfig: AppConfig) {
        // getMetadata
        // PUT /admin/v1/applications with metadataUrl (secured with user pw)
    }
}
