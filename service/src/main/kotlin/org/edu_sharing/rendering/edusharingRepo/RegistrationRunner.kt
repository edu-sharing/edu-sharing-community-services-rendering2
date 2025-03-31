package org.edu_sharing.rendering.edusharingRepo

import org.edu_sharing.rendering.edusharingRepo.dto.ActivateOptionalModuleRequest
import org.edu_sharing.rendering.edusharingRepo.entity.RepositoryRegistrationConfig
import org.edu_sharing.rendering.edusharingRepo.services.PrivatePublicKeyService
import org.edu_sharing.rendering.edusharingRepo.services.RepositoryRegistrationService
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.security.InvalidKeyException

@Component
@Profile("!test")
@ConditionalOnProperty(name = ["app.repository.registration.enabled"], havingValue = "true")
class RegistrationRunner(
    private val privatePublicKeyService: PrivatePublicKeyService,
    private var repositoryRegistrationService: RepositoryRegistrationService,
    private var repositoryRegistrationConfig: RepositoryRegistrationConfig
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)


    override fun run(args: ApplicationArguments?) {
        if (!privatePublicKeyService.hasKeyPair()) {
            privatePublicKeyService.generateApplicationKeyPair()
        }

        repositoryRegistrationConfig.getAllRegistrations().forEach {
            var (registrationRequest, optionalModuleList, moduleSettings) = it
            try {
                val registration = repositoryRegistrationService.registerWithRepository(registrationRequest, true)
                optionalModuleList.forEach { module ->
                    repositoryRegistrationService.activateOptionalModule(
                        ActivateOptionalModuleRequest(
                            repoId = registration.repoId,
                            module = module,
                            credentials = moduleSettings[module]?.credentials
                        )
                    )
                    log.info("Optional module activated: $module.")
                }
                val orphanedSettingsKeys = moduleSettings.keys.subtract(optionalModuleList)
                if (orphanedSettingsKeys.isNotEmpty()) {
                    log.warn("Settings provided for modules: ${orphanedSettingsKeys.joinToString(",")}. These modules are not in the optional-modules list. Did you forget them?")
                }
                log.info("Registration completed for ${registrationRequest.url}.")
            } catch (e: InvalidKeyException) {
                log.error("Error while registering ${registrationRequest.url}: ${e.message}", e)
                throw RuntimeException("Registration failed for ${registrationRequest.url} with\n ${e.message}", e)
            } catch (e: Exception) {
                log.error(e.message, e)
                throw RuntimeException(e)
            }
        }
    }
}
