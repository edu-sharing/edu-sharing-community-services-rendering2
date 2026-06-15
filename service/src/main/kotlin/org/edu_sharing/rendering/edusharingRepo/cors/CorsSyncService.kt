package org.edu_sharing.rendering.edusharingRepo.cors

import org.edu_sharing.generated.repository.backend.services.rest.client.ApiClient
import org.edu_sharing.generated.repository.backend.services.rest.client.api.RenderingV1Api
import org.edu_sharing.generated.repository.backend.services.rest.client.model.ApplicationSimple
import org.edu_sharing.rendering.core.annotation.ConditionalOnMasterOrController
import org.edu_sharing.rendering.edusharingRepo.AuthHeaderProvider
import org.edu_sharing.rendering.edusharingRepo.RestClientProvider
import org.edu_sharing.rendering.edusharingRepo.entity.RepositoryRegistration
import org.edu_sharing.rendering.edusharingRepo.repository.RepositoryRegistrationRepository
import org.edu_sharing.rendering.security.cors.CorsConfig
import org.springframework.amqp.core.AmqpTemplate
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
@ConditionalOnMasterOrController
class CorsSyncService(
    private val repositoryRegistrationRepository: RepositoryRegistrationRepository,
    private val restClientProvider: RestClientProvider,
    private val amqpTemplate: AmqpTemplate,
    private val corsConfig: CorsConfig,
    private val authHeaderProvider: AuthHeaderProvider,
    @Value("\${app.queue.controllerBroadcastExchange}")
    private val broadcastExchange: String,
    @Value("\${app.appId}")
    private val appId: String,
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
        log.info("Syncing allowed origins with all connected repositories finished")
    }

    fun triggerSync() {
        amqpTemplate.convertAndSend(broadcastExchange, "", "sync")
    }

    fun syncAllowedOriginsWithRepository(repoId: String): Boolean {
        val registration = repositoryRegistrationRepository.findByRepoId(repoId)
        if (registration.isEmpty) {
            log.warn("Repository $repoId not found. Skipping sync.")
            return false
        }
        return syncAllowedOriginsWithRepository(registration.get())
    }

    fun syncAllowedOriginsWithRepository(repositoryRegistration: RepositoryRegistration): Boolean {
        var changesDetected = false
        val aboutClient = restClientProvider.getAboutApiClient(repositoryRegistration.url)
        val about = aboutClient.about()
        val lastCacheUpdate = about.lastCacheUpdate ?: 0
        if (lastCacheUpdate > repositoryRegistration.lastAllowedOriginSync) {
            log.info("Cache update detected since last sync with repo ${repositoryRegistration.repoId}. Last sync: ${repositoryRegistration.lastAllowedOriginSync}")

            val allowedOriginPatternsFromRepo = mutableSetOf<String>()
            val allowedOriginsFromRepo = mutableSetOf<String>()

            val currentCachedOrigins = repositoryRegistration.allowedOrigins
            val currentCachedPatterns = repositoryRegistration.allowedOriginPatterns

            repositoryRegistration.lastAllowedOriginSync = lastCacheUpdate
            val applications = getApplicationInfo(repositoryRegistration.url, repositoryRegistration.repoId)
            applications.forEach { application ->
                if (application.id == repositoryRegistration.repoId) {
                    allowedOriginsFromRepo.addAll(application.allowedOrigins ?: emptyList())
                    return@forEach
                }
                if (application.id == appId) {
                    return@forEach
                }
                if (application.type == "LMS") {
                    allowedOriginsFromRepo.add("https://" + application.domain)
                    allowedOriginPatternsFromRepo.addAll(application.allowedOrigins ?: emptyList())
                }
            }

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

    private fun getApplicationInfo (url: String, repoId: String): List<ApplicationSimple> {
        val apiClient = ApiClient()
        apiClient.basePath = "${url}/rest"
        authHeaderProvider.getAuthHeaders(repoId).forEach { (key, value) -> apiClient.addDefaultHeader(key, value) }
        val renderingClient = RenderingV1Api(apiClient)
        return renderingClient.applications1
    }
}
