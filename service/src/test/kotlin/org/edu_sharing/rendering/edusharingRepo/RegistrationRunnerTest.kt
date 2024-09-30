package org.edu_sharing.rendering.edusharingRepo

import io.mockk.*
import io.mockk.junit5.MockKExtension
import org.edu_sharing.rendering.edusharingRepo.services.PrivatePublicKeyService
import org.edu_sharing.rendering.edusharingRepo.services.RepositoryRegistrationService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.ApplicationArguments
import java.security.InvalidKeyException

@ExtendWith(MockKExtension::class)
class RegistrationRunnerTest {
    private val keyService = mockk<PrivatePublicKeyService>()
    private val registrationService = mockk<RepositoryRegistrationService>()

    lateinit var underTest: RegistrationRunner

    @BeforeEach
    fun setup() {
        underTest = RegistrationRunner(
            keyService,
            registrationService,
        )
        clearAllMocks()
    }

    @Test
    fun testRunGeneratesNewKeyPairIfNonePresentAndGetsRepoKeyIfNotPresent() {
        // Arrange
        every { keyService.hasKeyPair() } returns false
        justRun { keyService.generateApplicationKeyPair() }
        every { keyService.hasRepositoryKey() } returns false
        justRun { registrationService.registerWithRepository() }

        // Act
        underTest.run(mockk<ApplicationArguments>())

        // Assert
        verifySequence {
            keyService.hasKeyPair()
            keyService.generateApplicationKeyPair()
            keyService.hasRepositoryKey()
            registrationService.registerWithRepository()
        }
    }

    @Test
    fun testRunDoesNotDoAnythingIfAllKeysArePresent() {
        // Arrange
        every { keyService.hasKeyPair() } returns true
        every { keyService.hasRepositoryKey() } returns true

        // Act
        underTest.run(mockk<ApplicationArguments>())

        // Assert
        verifySequence {
            keyService.hasKeyPair()
            keyService.hasRepositoryKey()
        }
    }

    @Test
    fun testRunOnlyCreatesKeyPairIfNonePresentButRepoKeyPresent() {
        // Arrange
        every { keyService.hasKeyPair() } returns false
        justRun { keyService.generateApplicationKeyPair() }
        every { keyService.hasRepositoryKey() } returns true

        // Act
        underTest.run(mockk<ApplicationArguments>())

        // Assert
        verifySequence {
            keyService.hasKeyPair()
            keyService.generateApplicationKeyPair()
            keyService.hasRepositoryKey()
        }
    }

    @Test
    fun testRunOnlyGetsRepoKeyIfNotPresentButKeypairPresent() {
        // Arrange
        every { keyService.hasKeyPair() } returns true
        every { keyService.hasRepositoryKey() } returns false
        justRun { registrationService.registerWithRepository() }

        // Act
        underTest.run(mockk<ApplicationArguments>())

        // Assert
        verifySequence {
            keyService.hasKeyPair()
            keyService.hasRepositoryKey()
            registrationService.registerWithRepository()
        }
    }

    @Test
    fun testRunDoesNotThrowInvalidKeyExceptionIfThrownByService() {
        // Arrange
        every { keyService.hasKeyPair() } returns true
        every { keyService.hasRepositoryKey() } returns false
        every { registrationService.registerWithRepository() } throws InvalidKeyException()

        // Act
        assertDoesNotThrow { underTest.run(mockk<ApplicationArguments>()) }

        // Assert
        verifySequence {
            keyService.hasKeyPair()
            keyService.hasRepositoryKey()
            registrationService.registerWithRepository()
        }
    }

    @Test
    fun testRunDoesNotThrowExceptionIfThrownByService() {
        // Arrange
        every { keyService.hasKeyPair() } returns true
        every { keyService.hasRepositoryKey() } returns false
        every { registrationService.registerWithRepository() } throws Exception()

        // Act
        assertDoesNotThrow { underTest.run(mockk<ApplicationArguments>()) }

        // Assert
        verifySequence {
            keyService.hasKeyPair()
            keyService.hasRepositoryKey()
            registrationService.registerWithRepository()
        }
    }
}
