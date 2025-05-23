package org.edu_sharing.rendering.edusharingRepo.cors

import org.edu_sharing.rendering.core.annotation.ConditionalOnMasterOrController
import org.edu_sharing.rendering.edusharingRepo.EncryptionService
import org.edu_sharing.rendering.edusharingRepo.RestClientProvider
import org.edu_sharing.rendering.edusharingRepo.entity.RepositoryRegistration
import org.edu_sharing.rendering.edusharingRepo.repository.RepositoryRegistrationRepository
import org.edu_sharing.rendering.security.cors.CorsConfig
import org.springframework.amqp.core.AmqpTemplate
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.*

@Service
@ConditionalOnMasterOrController
class CorsSyncService(
    private val repositoryRegistrationRepository: RepositoryRegistrationRepository,
    private val restClientProvider: RestClientProvider,
    private val encryptionService: EncryptionService,
    private val amqpTemplate: AmqpTemplate,
    private val corsConfig: CorsConfig,
    @Value("\${app.queue.controllerBroadcastExchange}")
    private val broadcastExchange: String
) {
    private val log = org.slf4j.LoggerFactory.getLogger(javaClass)

    init {
        applyKnownOrigins()
    }

    fun syncAllowedOriginsWithAllRepositories() {
        log.info("Syncing allowed origins with all connected repositories")
        val allRegistrations = repositoryRegistrationRepository.findAll()
        val results = allRegistrations.map { syncAllowedOriginsWithRepository(it) }
        val changesDetected = results.any { it }
        if (changesDetected) {
            log.info("Changes detected in cors settings from repositories. Triggering controller sync.")
            triggerSync()
        } else {
            log.info("No changes detected in cors settings from repositories. Skipping sync.")
        }
        //applyKnownOrigins()
        log.info("Syncing allowed origins with all connected repositories finished")
    }

    fun triggerSync() {
        amqpTemplate.convertAndSend(broadcastExchange, "", "sync")
    }

    fun syncAllowedOriginsWithRepository(repositoryRegistration: RepositoryRegistration): Boolean {
        var changesDetected = false
        val aboutClient = restClientProvider.getAboutApiClient(repositoryRegistration.url)
        val about = aboutClient.about()
        if (about.lastCacheUpdate > repositoryRegistration.lastAllowedOriginSync) {
            log.info("Cache update detected since last sync with repo ${repositoryRegistration.repoId}. Last sync: ${repositoryRegistration.lastAllowedOriginSync}")
            val adminClient = restClientProvider.getAdminV1Client(
                url = repositoryRegistration.url,
                password = encryptionService.decrypt(repositoryRegistration.repositoryPassword),
                username = repositoryRegistration.repositoryUser
            )
            val repoProperties = adminClient.getApplicationXML("homeApplication.properties.xml")
            val allowedOriginsFromRepo = repoProperties.getOrDefault("allow_origin", "")
                .split(",")
                .map { it.trim() }
                .toMutableSet()

            val allowedOriginPatternsFromRepo = mutableSetOf<String>()
            val applications = adminClient.applications

            applications.forEach { application ->
                val xml = application.xml
                val props = Properties()
                props.loadFromXML(xml.byteInputStream())
                if (application.type == "LMS") {
                    val domain = props.getProperty("domain")
                    if (!domain.isNullOrBlank()) {
                        allowedOriginsFromRepo.add(domain)
                    }
                }
                val patterns = props.getProperty("allow_origin")
                if (!patterns.isNullOrBlank()) {
                    allowedOriginPatternsFromRepo.addAll(
                        patterns.split(",")
                            .map { it.trim() }
                    )
                }
            }
            val currentCachedOrigins = repositoryRegistration.allowedOrigins
            val currentCachedPatterns = repositoryRegistration.allowedOriginPatterns

            repositoryRegistration.lastAllowedOriginSync = about.lastCacheUpdate

            if (currentCachedOrigins != allowedOriginsFromRepo) {
                log.info("Detected changes in cors origins for repo ${repositoryRegistration.repoId}. Current cached origins: $currentCachedOrigins. Repo origins: $allowedOriginsFromRepo")
                repositoryRegistration.allowedOrigins = allowedOriginsFromRepo
                changesDetected = true
            }

            if (currentCachedPatterns != allowedOriginPatternsFromRepo) {
                log.info("Detected changes in cors patterns for repo ${repositoryRegistration.repoId}. Current cached patterns: $currentCachedPatterns. Repo patterns: $allowedOriginPatternsFromRepo")
                repositoryRegistration.allowedOriginPatterns = allowedOriginPatternsFromRepo
                changesDetected = true
            }
            repositoryRegistrationRepository.save(repositoryRegistration)

        } else {
            log.info("Cors allowed origins and patterns are up to date for repo ${repositoryRegistration.repoId}. Skipping sync. Last sync: ${repositoryRegistration.lastAllowedOriginSync}")
        }
        return changesDetected
    }

    fun applyKnownOrigins() {
        val allRepoConfigs = repositoryRegistrationRepository.findAll()
        val origins = allRepoConfigs.flatMap { it.allowedOrigins }
            .toSet()
        val originPatterns = allRepoConfigs
            .flatMap { it.allowedOriginPatterns ?: emptySet() }
            .toSet()
        corsConfig.updateAllowedOrigins(origins)
        corsConfig.updateAllowedPatterns(originPatterns)
        corsConfig.updateCorsConfiguration()
    }
}