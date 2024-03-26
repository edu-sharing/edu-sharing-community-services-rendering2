package org.edu_sharing.rendering.service

import org.edu_sharing.rendering.entity.AppConfig
import org.edu_sharing.rendering.repository.mongo.AppConfigRepository
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.security.InvalidKeyException
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.*

@Service
class PrivatePublicKeyService (
    private val appConfigRepository: AppConfigRepository
) {
    private fun getConfig(): AppConfig {
        return appConfigRepository.findByIdOrNull("0") ?: throw IllegalStateException("No app config found")
    }

    @Cacheable(cacheNames = ["publicKey"], unless = "true")
    fun getRepoPublicKey(): PublicKey {
        val config = getConfig()
        val publicKey = config.repoPublicKey ?: throw InvalidKeyException("No public key available. Please register the application with an edu-sharing repository first")
        val publicKeyData = publicKey
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\n","")

        val keySpec = X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyData))
        return KeyFactory.getInstance("RSA").generatePublic(keySpec)
    }

    @CacheEvict(cacheNames = ["publicKey"])
    fun evictRepoPublicKey(){ }

    @Cacheable(cacheNames = ["privateKey"], unless = "true")
    fun getPrivateKey(): PrivateKey {
        val config = getConfig()
        val privateKey = config.privateKey ?: throw InvalidKeyException("No private key available. Please set up your service first")
        val keySpec = PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKey))
        return KeyFactory.getInstance("RSA").generatePrivate(keySpec)
    }

    @CacheEvict(cacheNames = ["privateKey"])
    fun evictPrivateKey(){ }
}
