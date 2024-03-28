package org.edu_sharing.rendering.runner

import org.edu_sharing.rendering.service.PrivatePublicKeyService
import org.edu_sharing.rendering.service.RepositoryRegistrationService
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.security.InvalidKeyException

@Component
class RegistrationRunner(
    private val privatePublicKeyService: PrivatePublicKeyService,
    private var repositoryRegistrationService: RepositoryRegistrationService
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun run(args: ApplicationArguments?) {
        if (!privatePublicKeyService.hasKeyPaare()) {
            privatePublicKeyService.generateApplicationKeyPair()
        }

        if (!privatePublicKeyService.hasRepositoryKey()) {
            try {
                repositoryRegistrationService.registerWithRepository()
                log.info("Registration completed")
            } catch (e: InvalidKeyException) {
                log.warn("Registration uncompleted: {}", e.message, e)
            } catch (e: Exception) {
                log.error(e.message, e)
            }
        }
    }
}
