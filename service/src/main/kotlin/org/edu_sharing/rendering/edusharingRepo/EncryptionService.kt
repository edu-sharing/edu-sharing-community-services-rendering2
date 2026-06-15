package org.edu_sharing.rendering.edusharingRepo

import org.edu_sharing.rendering.edusharingRepo.services.PrivatePublicKeyService
import org.edu_sharing.rendering.edusharingRepo.services.RepositoryRegistrationStorageService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.security.Signature

@Service
class EncryptionService(
    private val privatePublicKeyService: PrivatePublicKeyService,
    private val repositoryRegistrationStorageService: RepositoryRegistrationStorageService,
    ) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun sign(toSign: String, repoId: String): ByteArray {
        val privateKey = privatePublicKeyService.getPrivateKey()
        val dsa = Signature.getInstance(getSigningAlg(repoId))
        dsa.initSign(privateKey)
        dsa.update(toSign.toByteArray())
        return dsa.sign()
    }

    fun getSigningAlg(repoId: String): String {
        val algo = repositoryRegistrationStorageService
            .getRegistrationByRepoId(repoId)
            .orElseThrow { IllegalArgumentException("Repository registration not found for id: $repoId") }
            .signingAlgorithm
        log.info("Using signing algorithm $algo for repository $repoId")
        return algo
    }
}
