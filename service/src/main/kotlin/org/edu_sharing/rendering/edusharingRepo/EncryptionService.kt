package org.edu_sharing.rendering.edusharingRepo

import org.edu_sharing.rendering.edusharingRepo.services.PrivatePublicKeyService
import org.springframework.stereotype.Service
import java.security.Signature

@Service
class EncryptionService(
    private val privatePublicKeyService: PrivatePublicKeyService
) {
    fun sign(toSign: String): ByteArray {
        val privateKey = privatePublicKeyService.getPrivateKey()
        val dsa = Signature.getInstance("SHA1withRSA")
        dsa.initSign(privateKey)
        dsa.update(toSign.toByteArray())
        return dsa.sign()
    }
}
