package org.edu_sharing.rendering.edusharingRepo

import org.edu_sharing.rendering.edusharingRepo.services.PrivatePublicKeyService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.security.Signature
import kotlin.jvm.javaClass

@Service
class EncryptionService(
    private val privatePublicKeyService: PrivatePublicKeyService,
    @param:Value($$"${app.security.signing.alg}")
    private val signingAlg: String //@TODO individual config for repo
) {

    fun sign(toSign: String): ByteArray {
        val privateKey = privatePublicKeyService.getPrivateKey()
        val dsa = Signature.getInstance(getSigningAlg())
        dsa.initSign(privateKey)
        dsa.update(toSign.toByteArray())
        return dsa.sign()
    }

    fun getSigningAlg(): String {
        return signingAlg
    }
}
