package org.edu_sharing.rendering.edusharingRepo.services

import org.edu_sharing.rendering.core.annotation.ConditionalOnController
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.Signature

/**
 * Verifies data signed by a registered repository with the public key of its registration
 * ([RepositoryPublicKeyService]). Used by `/public/renderdata` (signed node) and `/public/version` (signed app id + ts).
 */
@Component
@ConditionalOnController
class RepositorySignatureVerifier(
    private val repositoryPublicKeyService: RepositoryPublicKeyService,
    @param:Value($$"#{'${app.security.allowed-signature-algorithms:SHA256withRSA,SHA384withRSA,SHA512withRSA}'.split(',')}")
    private val allowedSignatureAlgorithms: List<String>,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * @throws IllegalArgumentException if the algorithm is not allowed or the repository is not registered
     * @throws IllegalStateException if the signature does not match
     */
    fun verify(repoId: String, data: ByteArray, signature: ByteArray, algorithm: String) {
        require(algorithm in allowedSignatureAlgorithms) {
            "Signature algorithm '$algorithm' is not allowed"
        }
        val verifier = Signature.getInstance(algorithm)
        verifier.initVerify(repositoryPublicKeyService.getRepositoryKey(repoId))
        verifier.update(data)
        if (!verifier.verify(signature)) {
            log.debug("Signature verification failed: repoId=$repoId, algorithm=$algorithm")
            throw IllegalStateException("Signature verification failed")
        }
        log.debug("Signature verification passed: repoId=$repoId, algorithm=$algorithm")
    }
}
