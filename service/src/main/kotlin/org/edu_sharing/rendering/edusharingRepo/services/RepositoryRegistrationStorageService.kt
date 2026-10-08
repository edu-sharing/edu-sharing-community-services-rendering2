package org.edu_sharing.rendering.edusharingRepo.services

import org.edu_sharing.rendering.edusharingRepo.cache.RegistrationCacheBroadcaster
import org.edu_sharing.rendering.edusharingRepo.cache.RegistrationCacheConfig
import org.edu_sharing.rendering.edusharingRepo.entity.RepositoryRegistration
import org.edu_sharing.rendering.edusharingRepo.entity.RepositoryRegistrationConfig
import org.edu_sharing.rendering.edusharingRepo.repository.RepositoryRegistrationRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.CachePut
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.util.*

@Service
class RepositoryRegistrationStorageService(
    private val repoRegistrationRepository: RepositoryRegistrationRepository,
    private val repositoryRegistrationConfig: RepositoryRegistrationConfig,
    private val registrationCacheBroadcaster: RegistrationCacheBroadcaster,
    @param:Value($$"${app.security.enabled}")
    private val securityEnabled: Boolean
) {

    companion object {
        const val TEST_PREFIX = "TEST"
        private val log = LoggerFactory.getLogger(RepositoryRegistrationStorageService::class.java)
    }

    /**
     * Cached per pod ([RegistrationCacheConfig]); other pods are told to drop their copy on every change
     * ([RegistrationCacheBroadcaster]). A repoId without registration is not cached (`unless`), so a registration
     * created later is found at once. Do not mutate the returned entity to then save it - use
     * [getRegistrationByRepoIdFresh] for read-modify-write.
     */
    @Cacheable(RegistrationCacheConfig.REGISTRATIONS, key = "#repoId", unless = "#result == null")
    fun getRegistrationByRepoId(repoId: String): Optional<RepositoryRegistration> {
        log.debug("Cache miss for registration, loading from database for repoId: $repoId")
        return loadRegistration(repoId)
    }

    /**
     * Reads the registration straight from the database, bypassing the cache. For read-modify-write: the cached
     * instance is shared between threads and may be older than the stored one (e.g. CORS origins synced since).
     */
    fun getRegistrationByRepoIdFresh(repoId: String): Optional<RepositoryRegistration> = loadRegistration(repoId)

    private fun loadRegistration(repoId: String): Optional<RepositoryRegistration> {
        if (!securityEnabled && repoId.startsWith(TEST_PREFIX)) {
            val localConfig = repositoryRegistrationConfig.id["local"]
            val registration = RepositoryRegistration(
                repoId = repoId,
                url = "",
                publicKey = UUID.randomUUID().toString(),
                optionalModules = localConfig?.optionalModules?.toMutableList() ?: mutableListOf(),
                module = localConfig?.module?.toMutableMap() ?: mutableMapOf(),
                quota = localConfig?.quota?.toBytes() ?: 0L,
                buckets = localConfig?.externalBuckets?.toExternalBuckets(),
                signingAlgorithm = "SHA1withRSA"
            )
            return Optional.of<RepositoryRegistration>(registration)
        }
        return repoRegistrationRepository.findByRepoId(repoId)
    }

    @CachePut(RegistrationCacheConfig.REGISTRATIONS, key = "#registration.repoId")
    fun storeRegistration(registration: RepositoryRegistration): RepositoryRegistration {
        log.debug("Storing registration for repoId=${registration.repoId} (url=${registration.url})")
        val stored = repoRegistrationRepository.save(registration)
        registrationCacheBroadcaster.registrationChanged(stored.repoId)
        return stored
    }

    @CacheEvict(RegistrationCacheConfig.REGISTRATIONS, key = "#repoId")
    fun removeRegistration(repoId: String) : Optional<RepositoryRegistration> {
        log.debug("Removing registration and evicting cache for repoId: $repoId")
        val removed = repoRegistrationRepository.removeByRepoId(repoId)
        registrationCacheBroadcaster.registrationChanged(repoId)
        return removed
    }


    fun getRegistrationCount() = repoRegistrationRepository.count()
    fun clearRegistrations() {
        repoRegistrationRepository.deleteAll()
    }

    fun getRegistrations(): List<RepositoryRegistration> {
        return repoRegistrationRepository.findAll()
    }
}
