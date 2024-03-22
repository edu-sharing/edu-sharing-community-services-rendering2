package org.edu_sharing.rendering.security.jwt

import org.edu_sharing.rendering.service.MetadataService
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.*

@Service
class RepositoryPublicKeyProvider(
    private val metadataService: MetadataService) {

    @Cacheable(cacheNames = ["publicKey"], unless = "true")
    fun getPublicKey() : PublicKey? {
        val metadata = metadataService.getMetadata()
        val publicKey = metadata.repoPublicKey ?: return null;

        val publicKeyData = publicKey
            .replace("-----BEGIN PUBLIC KEY-----\n", "")
            .replace("-----END PUBLIC KEY-----", "")

        val keySpec = X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyData))
        return KeyFactory.getInstance("RSA").generatePublic(keySpec)
    }

    @CacheEvict(cacheNames = ["publicKey"])
    fun evictPublicKey(){ }
}