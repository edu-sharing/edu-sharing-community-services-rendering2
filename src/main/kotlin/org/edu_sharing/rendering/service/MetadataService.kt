package org.edu_sharing.rendering.service

import org.edu_sharing.rendering.entity.AppConfig
import org.edu_sharing.rendering.repository.mongo.AppConfigRepository
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.security.*
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.*

@Service
class MetadataService(
    private val repository: AppConfigRepository
) : PrivatePublicKeyService {

    fun getConfig(): AppConfig {
        return repository.findById("0")
            .orElse(AppConfig())
//            .orElseThrow { throw EntryNotFoundException("No config found in database.") }
    }

    fun storeConfig(appConfig: AppConfig) {
        repository.save(appConfig)
    }

    override fun hasKeyPaare() : Boolean {
        return !getConfig().privateKey.isNullOrBlank() && !getConfig().publicKey.isNullOrBlank()
    }

    override fun hasRepositoryKey(): Boolean {
        return !getConfig().repoPublicKey.isNullOrBlank()
    }


    @CacheEvict(cacheNames = ["privateKey"])
    override fun generateApplicationKeyPair() {
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048)
        val keyPair = generator.generateKeyPair()

        val appConfig = getConfig()
        appConfig.privateKey = Base64.getEncoder().encode(keyPair.private.encoded).decodeToString()
        appConfig.publicKey = "-----BEGIN PUBLIC KEY-----\n" +
                Base64.getEncoder().encode(keyPair.public.encoded).decodeToString() +
                "-----END PUBLIC KEY-----"
        storeConfig(appConfig)
    }

    @CacheEvict(cacheNames = ["repoPublicKey"])
    override fun storeRepositoryKey(publicKey: String) {
        val config = getConfig()
        config.repoPublicKey = publicKey
        storeConfig(config)
    }

    @Throws(InvalidKeyException::class)
    @Cacheable(cacheNames = ["repoPublicKey"], unless = "true")
    override fun getRepositoryKey(): PublicKey {
        val config = getConfig()
        val publicKey = config.repoPublicKey
            ?: throw InvalidKeyException("No public key available. Please register the application with an edu-sharing repository first")
        val publicKeyData = publicKey
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\n", "")

        val keySpec = X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyData))
        return KeyFactory.getInstance("RSA").generatePublic(keySpec)
    }

    @Cacheable(cacheNames = ["privateKey"], unless = "true")
    override fun getPrivateKey(): PrivateKey {
        val config = getConfig()
        val privateKey =
            config.privateKey ?: throw InvalidKeyException("No private key available. Please set up your service first")
        val keySpec = PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKey))
        return KeyFactory.getInstance("RSA").generatePrivate(keySpec)
    }
}