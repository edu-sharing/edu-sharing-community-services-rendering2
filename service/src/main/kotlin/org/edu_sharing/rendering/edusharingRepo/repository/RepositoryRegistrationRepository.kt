package org.edu_sharing.rendering.edusharingRepo.repository

import org.edu_sharing.rendering.edusharingRepo.entity.RepositoryRegistration
import org.springframework.data.mongodb.repository.MongoRepository
import java.util.*

interface RepositoryRegistrationRepository : MongoRepository<RepositoryRegistration, String> {
    fun removeByRepoId(repoId: String): Optional<RepositoryRegistration>
    fun findByRepoId(repoId: String): Optional<RepositoryRegistration>
    fun findByUrl(url: String): Optional<RepositoryRegistration>
}
