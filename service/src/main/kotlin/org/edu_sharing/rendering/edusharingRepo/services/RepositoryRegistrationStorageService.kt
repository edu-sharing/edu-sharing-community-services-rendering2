package org.edu_sharing.rendering.edusharingRepo.services

import org.edu_sharing.rendering.edusharingRepo.entity.RepositoryRegistration
import org.edu_sharing.rendering.edusharingRepo.repository.RepositoryRegistrationRepository
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.CachePut
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.util.*

@Service
class RepositoryRegistrationStorageService(
    private val repoRegistrationRepository: RepositoryRegistrationRepository
) {

    companion object {
        const val TEST_PREFIX = "TEST"
    }

    @Cacheable("registrations", key = "#repoId")
    fun getRegistrationByRepoId(repoId: String): Optional<RepositoryRegistration> {
        if (repoId.startsWith(TEST_PREFIX)) {
            val registration = RepositoryRegistration(
                repoId = repoId,
                url = "",
                publicKey = UUID.randomUUID().toString(),
                optionalModules = mutableListOf("H5P", "JUPYTER", "EDUHTML")
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
