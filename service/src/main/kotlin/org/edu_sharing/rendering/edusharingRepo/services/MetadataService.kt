package org.edu_sharing.rendering.edusharingRepo.services

import org.edu_sharing.rendering.edusharingRepo.entity.RendererKeyConfig
import org.edu_sharing.rendering.edusharingRepo.repository.RendererKeyConfigRepository
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.security.InvalidKeyException
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.*

@Service
class MetadataService(
    private val repository: RendererKeyConfigRepository
) : PrivatePublicKeyService {

    fun getConfig(): RendererKeyConfig {
        return repository.findById("0")
            .orElse(RendererKeyConfig())
//            .orElseThrow { throw EntryNotFoundException("No config found in database.") }
    }

    private fun storeConfig(rendererKeyConfig: RendererKeyConfig) {
        repository.save(rendererKeyConfig)
    }

    override fun hasKeyPair() : Boolean {
        val config = getConfig()
        return !config.privateKey.isNullOrBlank() && !config.publicKey.isNullOrBlank()
    }


    @CacheEvict("privateKey")
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

//    @CacheEvict(cacheNames = ["repoPublicKey"])
//    override fun storeRepositoryKey(publicKey: String) {
//        val config = getConfig()
//        config.repoPublicKey = publicKey
//        storeConfig(config)
//    }
//
//    @Throws(InvalidKeyException::class)
//    @Cacheable(cacheNames = ["repoPublicKey"], unless = "true")
//    override fun getRepositoryKey(): PublicKey {
//        val config = getConfig()
//        val publicKey = config.repoPublicKey
//            ?: throw InvalidKeyException("No public key available. Please register the application with an edu-sharing repository first")
//        val publicKeyData = publicKey
//            .replace("-----BEGIN PUBLIC KEY-----", "")
//            .replace("-----END PUBLIC KEY-----", "")
//            .replace("\n", "")
//
//        val keySpec = X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyData))
//        return KeyFactory.getInstance("RSA").generatePublic(keySpec)
//    }

    @Cacheable("privateKey")
    override fun getPrivateKey(): PrivateKey {
        val config = getConfig()
        val privateKey =
            config.privateKey ?: throw InvalidKeyException("No private key available. Please set up your service first")
        val keySpec = PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKey))
        return KeyFactory.getInstance("RSA").generatePrivate(keySpec)
    }
}
