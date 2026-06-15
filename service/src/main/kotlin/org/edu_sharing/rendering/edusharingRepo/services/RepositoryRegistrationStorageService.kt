package org.edu_sharing.rendering.edusharingRepo.services

import org.edu_sharing.rendering.edusharingRepo.entity.RepositoryRegistration
import org.edu_sharing.rendering.edusharingRepo.entity.RepositoryRegistrationConfig
import org.edu_sharing.rendering.edusharingRepo.repository.RepositoryRegistrationRepository
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
    @param:Value("\${app.security.enabled}")
    private val securityEnabled: Boolean
) {

    companion object {
        const val TEST_PREFIX = "TEST"
    }

    @Cacheable("registrations", key = "#repoId")
    fun getRegistrationByRepoId(repoId: String): Optional<RepositoryRegistration> {
        if (!securityEnabled && repoId.startsWith(TEST_PREFIX)) {
            val localConfig = repositoryRegistrationConfig.id["local"]
            val registration = RepositoryRegistration(
                repoId = repoId,
                url = "",
                publicKey = UUID.randomUUID().toString(),
                optionalModules = localConfig?.optionalModules?.toMutableList() ?: mutableListOf(),
                module = localConfig?.module?.toMutableMap() ?: mutableMapOf(),
                quota = localConfig?.quota ?: 0L,
                buckets = localConfig?.externalBuckets,
                signingAlgorithm = "SHA1withRSA"
            )
            return Optional.of<RepositoryRegistration>(registration)
        }
        return repoRegistrationRepository.findByRepoId(repoId)
    }

    @CachePut("registrations", key = "#registration.repoId")
    fun storeRegistration(registration: RepositoryRegistration): RepositoryRegistration {
        return repoRegistrationRepository.save(registration)
    }

    @CacheEvict("registrations", key = "#repoId")
    fun removeRegistration(repoId: String) : Optional<RepositoryRegistration> =
        repoRegistrationRepository.removeByRepoId(repoId)


    fun getRegistrationCount() = repoRegistrationRepository.count()
    fun clearRegistrations() {
        repoRegistrationRepository.deleteAll()
    }

    fun getRegistrations(): List<RepositoryRegistration> {
        return repoRegistrationRepository.findAll()
    }
}
