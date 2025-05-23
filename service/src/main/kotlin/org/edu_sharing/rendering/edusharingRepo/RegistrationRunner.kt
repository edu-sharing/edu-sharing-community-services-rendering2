package org.edu_sharing.rendering.edusharingRepo

import org.edu_sharing.rendering.core.annotation.ConditionalOnMasterAndRegistration
import org.edu_sharing.rendering.edusharingRepo.cors.CorsSyncService
import org.edu_sharing.rendering.edusharingRepo.dto.ActivateOptionalModuleRequest
import org.edu_sharing.rendering.edusharingRepo.entity.RepositoryRegistrationConfig
import org.edu_sharing.rendering.edusharingRepo.repository.RepositoryRegistrationRepository
import org.edu_sharing.rendering.edusharingRepo.services.PrivatePublicKeyService
import org.edu_sharing.rendering.edusharingRepo.services.RepositoryRegistrationService
import org.edu_sharing.rendering.modules.ModuleRegistry
import org.edu_sharing.rendering.modules.RenderModule
import org.edu_sharing.rendering.modules.ThirdPartyModule
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.security.InvalidKeyException
import kotlin.jvm.optionals.getOrNull

@Component
@Profile("!test")
@ConditionalOnMasterAndRegistration
class RegistrationRunner(
    private val privatePublicKeyService: PrivatePublicKeyService,
    private var repositoryRegistrationService: RepositoryRegistrationService,
    private var repositoryRegistrationConfig: RepositoryRegistrationConfig,
    private val repositoryRegistrationRepository: RepositoryRegistrationRepository,
    private val corsSyncService: CorsSyncService,
    private val encryptionService: EncryptionService,
    private val moduleRegistry: ModuleRegistry,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments?) {
        if (!privatePublicKeyService.hasKeyPair()) {
            privatePublicKeyService.generateApplicationKeyPair()
        }
        val existingRegistrations = repositoryRegistrationRepository.findAll()
        existingRegistrations.forEach { registration ->
            try {
                val correspondingConfig = repositoryRegistrationConfig
                    .getAllRegistrations().firstOrNull { it.first.url == registration.url }
                if (correspondingConfig == null) {
                    return@forEach
                }
                if (correspondingConfig.first.username != registration.repositoryUser ||
                    correspondingConfig.first.password != encryptionService.decrypt(registration.repositoryPassword)
                ) {
                    repositoryRegistrationRepository.delete(registration)
                    log.info("Deleted registration for ${registration.url} because credentials have changed. It will be re-registered.")
                    return@forEach
                }
                val existingModules = registration.optionalModules
                val configModules = correspondingConfig.second
                configModules.forEach { module ->
                    val isAddedModule = !existingModules.contains(module)
                    val isUpdateToThirdPartyModule by lazy {
                        moduleRegistry.getRenderModule<RenderModule>(module) is ThirdPartyModule &&
                                correspondingConfig.third[module]?.credentials != registration.module[module]?.credentials
                    }
                    if (isAddedModule || isUpdateToThirdPartyModule) {
                        log.info("Adding or updating module $module to/in registration for ${registration.url}.")
                        repositoryRegistrationService.activateOptionalModule(
                            ActivateOptionalModuleRequest(
                                repoId = registration.repoId,
                                module = module,
                                credentials = correspondingConfig.third[module]?.credentials
                            )
                        )
                    }
                }
                registration.optionalModules.subtract(configModules).forEach { module ->
                    repositoryRegistrationService.removeOptionalModule(repoId = registration.repoId, module = module)
                }

            } catch (e: Exception) {
                log.error(e.message, e)
                throw RuntimeException(e)
            }
        }

        val newRegistrations = repositoryRegistrationConfig.getAllRegistrations()
            .filter { repositoryRegistrationRepository.findByUrl(it.first.url).getOrNull() == null }
            .map {
                val (registrationRequest, optionalModuleList, moduleSettings) = it
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
                    corsSyncService.syncAllowedOriginsWithRepository(registration)
                    log.info("Synced allowed origins for ${registration.repoId}.")
                    registration
                } catch (e: InvalidKeyException) {
                    log.error("Error while registering ${registrationRequest.url}: ${e.message}", e)
                    throw RuntimeException("Registration failed for ${registrationRequest.url} with\n ${e.message}", e)
                } catch (e: Exception) {
                    log.error(e.message, e)
                    throw RuntimeException(e)
                }
            }
        if (newRegistrations.isNotEmpty()) {
            corsSyncService.triggerSync()
        }
    }
}
