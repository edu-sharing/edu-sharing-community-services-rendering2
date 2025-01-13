package org.edu_sharing.rendering.edusharingRepo.services

import io.mockk.*
import io.mockk.junit5.MockKExtension
import org.edu_sharing.rendering.edusharingRepo.entity.RendererKeyConfig
import org.edu_sharing.rendering.edusharingRepo.repository.RendererKeyConfigRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.security.InvalidKeyException
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.*


@ExtendWith(MockKExtension::class)
class MetaDataServiceTest {
    private val repository = mockk<RendererKeyConfigRepository>()
    lateinit var underTest: MetadataService

    @BeforeEach
    fun setup() {
        underTest = MetadataService(repository)
    }

    @Test
    fun testGetConfigReturnsAppConfigReturnedFromRepository() {
        // Arrange
        val config = RendererKeyConfig(publicKey = "testKey")
        every { repository.findById("0") } returns Optional.of(config)

        // Act
        val result = underTest.getConfig()

        // Assert
        assert(result == config)

        verify(exactly = 1) { repository.findById("0") }
        confirmVerified(repository)
    }

    @Test
    fun testGetConfigReturnsEmptyConfigIfNonFound() {
        // Arrange
        every { repository.findById("0") } returns Optional.empty()

        // Act
        val result = underTest.getConfig()

        // Assert
        assert(result.id == "0")
        assert(result.publicKey == null)
        assert(result.privateKey == null)
        assert(result.version == null)
        assert(result.publicKey == null)

        verify(exactly = 1) { repository.findById("0") }
        confirmVerified(repository)
    }

    @Test
    fun testHasKeyPairReturnsFalseIfBothKeysAreNull() {
        // Arrange
        every { repository.findById("0") } returns Optional.of(RendererKeyConfig())

        // Act
        val result = underTest.hasKeyPair()

        // Assert
        assert(!result)
        verify(exactly = 1) { repository.findById("0") }
        confirmVerified(repository)
    }

    @Test
    fun testHasKeyPairReturnsFalseIfBothKeysAreBlank() {
        // Arrange
        every { repository.findById("0") } returns Optional.of(
            RendererKeyConfig(
                publicKey = "",
                privateKey = "",
            )
        )

        // Act
        val result = underTest.hasKeyPair()

        // Assert
        assert(!result)
        verify(exactly = 1) { repository.findById("0") }
        confirmVerified(repository)
    }

    @Test
    fun testHasKeyPairReturnsFalseIfPrivateKeyIsBlank() {
        // Arrange
        every { repository.findById("0") } returns Optional.of(
            RendererKeyConfig(
                publicKey = "123",
                privateKey = "",
            )
        )

        // Act
        val result = underTest.hasKeyPair()

        // Assert
        assert(!result)
        verify(exactly = 1) { repository.findById("0") }
        confirmVerified(repository)
    }

    @Test
    fun testHasKeyPairReturnsFalseIfPrivateKeyIsNull() {
        // Arrange
        every { repository.findById("0") } returns Optional.of(
            RendererKeyConfig(
                publicKey = "123",
                privateKey = null,
            )
        )

        // Act
        val result = underTest.hasKeyPair()

        // Assert
        assert(!result)
        verify(exactly = 1) { repository.findById("0") }
        confirmVerified(repository)
    }

    @Test
    fun testHasKeyPairReturnsFalseIfPublicKeyIsBlank() {
        // Arrange
        every { repository.findById("0") } returns Optional.of(
            RendererKeyConfig(
                publicKey = "",
                privateKey = "123",
            )
        )

        // Act
        val result = underTest.hasKeyPair()

        // Assert
        assert(!result)
        verify(exactly = 1) { repository.findById("0") }
        confirmVerified(repository)
    }

    @Test
    fun testHasKeyPairReturnsFalseIfPublicKeyIsNull() {
        // Arrange
        every { repository.findById("0") } returns Optional.of(
            RendererKeyConfig(
                publicKey = null,
                privateKey = "123",
            )
        )

        // Act
        val result = underTest.hasKeyPair()

        // Assert
        assert(!result)
        verify(exactly = 1) { repository.findById("0") }
        confirmVerified(repository)
    }

    @Test
    fun testHasKeyPairReturnsTrueIfBothKeysArePresent() {
        // Arrange
        every { repository.findById("0") } returns Optional.of(
            RendererKeyConfig(
                publicKey = "123",
                privateKey = "123",
            )
        )

        // Act
        val result = underTest.hasKeyPair()

        // Assert
        assert(result)
        verify(exactly = 1) { repository.findById("0") }
        confirmVerified(repository)
    }

    @Test
    fun testGenerateApplicationKeyPairGeneratesAndStoresValidKeyPair() {
        // Arrange
        every { repository.findById("0") } returns Optional.of(
            RendererKeyConfig()
        )
        val configSlot = slot<RendererKeyConfig>()
        every { repository.save(capture(configSlot)) } returns mockk<RendererKeyConfig>()

        // Act
        underTest.generateApplicationKeyPair()

        // Assert
        val privateKeyString = configSlot.captured.privateKey ?: ""
        val publicKeyString = configSlot.captured.publicKey ?: ""

        // Is everything populated and matches the format?
        assert(publicKeyString.isNotBlank())
        assert(publicKeyString.startsWith("-----BEGIN PUBLIC KEY-----\n"))
        assert(publicKeyString.endsWith("-----END PUBLIC KEY-----"))
        assert(privateKeyString.isNotBlank())

        // Extract public key
        val publicKeyData = publicKeyString
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\n", "")

        // Sign something with private key
        val keySpec = PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKeyString))
        val privateKey = KeyFactory.getInstance("RSA").generatePrivate(keySpec)
        val sigData = "mydata"
        val dsa = Signature.getInstance("SHA1withRSA")
        dsa.initSign(privateKey)
        dsa.update(sigData.toByteArray())
        val signed = dsa.sign()

        // Verify with public key
        val verify = Signature.getInstance("SHA1withRSA")
        val publicKeySpec = X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyData))
        val publicKeyInstance = KeyFactory.getInstance("RSA").generatePublic(publicKeySpec)

        verify.initVerify(publicKeyInstance)
        verify.update(sigData.toByteArray())
        assert(verify.verify(signed))

        // Verify calls
        verifySequence {
            repository.findById("0")
            repository.save(any())
        }
    }

    /**
    @Test
    fun testStoreRepositoryKeyAddsRepoKeyToConfig() {
        // Arrange
        every { repository.findById("0") } returns Optional.of(
            RendererKeyConfig()
        )
        val configSlot = slot<RendererKeyConfig>()
        every { repository.save(capture(configSlot)) } returns mockk<RendererKeyConfig>()

        // Act
        underTest.storeRepositoryKey("somekey")

        // Assert
        assert(configSlot.captured.repoPublicKey == "somekey")

        verifySequence {
            repository.findById("0")
            repository.save(capture(configSlot))
        }
    }
    */

    @Test
    fun testGetPrivateKeyThrowsExceptionIfNotSetInConfig() {
        // Arrange
        every { repository.findById("0") } returns Optional.of(RendererKeyConfig())

        // Act and assert
        assertThrows<InvalidKeyException> { underTest.getPrivateKey() }

        verify(exactly = 1) { repository.findById("0")}
        confirmVerified(repository)
    }

    @Test
    fun testGetPrivateKeyReturnsKeyInstanceFromStoredKey() {
        // Arrange
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048)
        val keyPair = generator.generateKeyPair()
        val rendererKeyConfig = RendererKeyConfig()
        rendererKeyConfig.privateKey = Base64.getEncoder().encode(keyPair.private.encoded).decodeToString()

        every { repository.findById("0") } returns Optional.of(rendererKeyConfig)

        // Act and assert
        assertDoesNotThrow { underTest.getPrivateKey() }

        verify (exactly = 1) { repository.findById("0") }
        confirmVerified(repository)
    }
}
