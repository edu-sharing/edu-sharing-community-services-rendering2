package org.edu_sharing.rendering.edusharingRepo

import net.javacrumbs.shedlock.core.DefaultLockingTaskExecutor
import net.javacrumbs.shedlock.core.LockConfiguration
import net.javacrumbs.shedlock.core.LockProvider
import net.javacrumbs.shedlock.core.LockingTaskExecutor
import org.edu_sharing.rendering.core.annotation.ConditionalOnMasterAndRegistration
import org.edu_sharing.rendering.edusharingRepo.cors.CorsSyncService
import org.edu_sharing.rendering.edusharingRepo.dto.ActivateOptionalModuleRequest
import org.edu_sharing.rendering.edusharingRepo.entity.RepositoryRegistrationConfig
import org.edu_sharing.rendering.edusharingRepo.services.PrivatePublicKeyService
import org.edu_sharing.rendering.edusharingRepo.services.RepositoryRegistrationService
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.security.InvalidKeyException
import java.time.Duration
import java.time.Instant

@Component
@Profile("!test")
@ConditionalOnMasterAndRegistration
class RegistrationRunner(
    private val privatePublicKeyService: PrivatePublicKeyService,
    private var repositoryRegistrationService: RepositoryRegistrationService,
    private var repositoryRegistrationConfig: RepositoryRegistrationConfig,
    private val corsSyncService: CorsSyncService,
    lockProvider: LockProvider,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)
    private val lockingExecutor: LockingTaskExecutor = DefaultLockingTaskExecutor(lockProvider)

    override fun run(args: ApplicationArguments) {
        // Guard the one-time startup registration with a cluster-wide lock: if more than one
        // master instance starts concurrently, only one generates the key pair and registers
        // (force=true) — the others skip. lockAtMostFor bounds the hold so a crash mid-run
        // cannot deadlock startup. See MetadataService key-pair race in the plan.
        val lockConfiguration = LockConfiguration(
            Instant.now(),
            "repositoryRegistration",
            Duration.ofMinutes(5),
            Duration.ZERO,
        )
        val result = lockingExecutor.executeWithLock(
            LockingTaskExecutor.TaskWithResult { doRegister() },
            lockConfiguration,
        )
        if (!result.wasExecuted()) {
            log.info("Repository registration skipped — another master instance holds the lock")
        }
    }

    private fun doRegister() {
        log.debug("RegistrationRunner starting; checking application key pair")
        if (!privatePublicKeyService.hasKeyPair()) {
            log.debug("No existing key pair found; generating new key pair")
            privatePublicKeyService.generateApplicationKeyPair()
        } else {
            log.debug("Existing key pair present; skipping key generation")
        }
        val newRegistrations = repositoryRegistrationConfig.getAllRegistrations()
            .map {
                val (registrationRequest, optionalModuleList, moduleSettings) = it
                log.debug("Processing auto-registration for ${registrationRequest.url} with ${optionalModuleList.size} optional modules")
                try {
                    val registration = repositoryRegistrationService.registerWithRepository(request = registrationRequest, force = true, useInternal = true)
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
                    moduleSettings.forEach { module ->
                        repositoryRegistrationService.setCspHeader(
                            repoId = registration.repoId,
                            module = module.key,
                            cspHeader = module.value.cspHeader
                        )
                    }
                    val orphanedSettingsKeys = moduleSettings.keys.subtract(optionalModuleList.toSet())
                    if (orphanedSettingsKeys.isNotEmpty()) {
                        log.warn("Settings provided for modules: ${orphanedSettingsKeys.joinToString(",")}. These modules are not in the optional-modules list. Did you forget them?")
                    }
                    log.info("Registration completed for ${registrationRequest.url}.")
                    corsSyncService.syncAllowedOriginsWithRepository(registration.repoId)
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
