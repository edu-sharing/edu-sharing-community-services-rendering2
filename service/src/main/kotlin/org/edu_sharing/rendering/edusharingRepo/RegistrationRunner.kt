package org.edu_sharing.rendering.edusharingRepo

import org.edu_sharing.rendering.edusharingRepo.services.PrivatePublicKeyService
import org.edu_sharing.rendering.edusharingRepo.services.RepositoryRegistrationService
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.security.InvalidKeyException

@Component
@ConditionalOnProperty(name = ["edu_sharing.registration.enabled"], havingValue = "true")
class RegistrationRunner(
    private val privatePublicKeyService: PrivatePublicKeyService,
    private var repositoryRegistrationService: RepositoryRegistrationService
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun run(args: ApplicationArguments?) {
        if (!privatePublicKeyService.hasKeyPair()) {
            privatePublicKeyService.generateApplicationKeyPair()
        }

        if (!privatePublicKeyService.hasRepositoryKey()) {
            try {
                repositoryRegistrationService.registerWithRepository()
                log.info("Registration completed")
            } catch (e: InvalidKeyException) {
                log.warn("Registration incomplete: {}", e.message, e)
            } catch (e: Exception) {
                log.error(e.message, e)
            }
        }
    }
}
