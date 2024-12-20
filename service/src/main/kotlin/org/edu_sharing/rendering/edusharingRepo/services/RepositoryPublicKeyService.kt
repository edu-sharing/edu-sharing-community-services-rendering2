package org.edu_sharing.rendering.edusharingRepo.services

import java.security.PublicKey

interface RepositoryPublicKeyService {
    fun getRepositoryKey(repoId: String): PublicKey
}
