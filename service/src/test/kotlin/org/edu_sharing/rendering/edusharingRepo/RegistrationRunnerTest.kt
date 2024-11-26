package org.edu_sharing.rendering.edusharingRepo

/**
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
        justRun { registrationService.registerWithRepository(body) }

        // Act
        underTest.run(mockk<ApplicationArguments>())

        // Assert
        verifySequence {
            keyService.hasKeyPair()
            keyService.generateApplicationKeyPair()
            keyService.hasRepositoryKey()
            registrationService.registerWithRepository(body)
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
        justRun { registrationService.registerWithRepository(body) }

        // Act
        underTest.run(mockk<ApplicationArguments>())

        // Assert
        verifySequence {
            keyService.hasKeyPair()
            keyService.hasRepositoryKey()
            registrationService.registerWithRepository(body)
        }
    }

    @Test
    fun testRunDoesNotThrowInvalidKeyExceptionIfThrownByService() {
        // Arrange
        every { keyService.hasKeyPair() } returns true
        every { keyService.hasRepositoryKey() } returns false
        every { registrationService.registerWithRepository(body) } throws InvalidKeyException()

        // Act
        assertDoesNotThrow { underTest.run(mockk<ApplicationArguments>()) }

        // Assert
        verifySequence {
            keyService.hasKeyPair()
            keyService.hasRepositoryKey()
            registrationService.registerWithRepository(body)
        }
    }

    @Test
    fun testRunDoesNotThrowExceptionIfThrownByService() {
        // Arrange
        every { keyService.hasKeyPair() } returns true
        every { keyService.hasRepositoryKey() } returns false
        every { registrationService.registerWithRepository(body) } throws Exception()

        // Act
        assertDoesNotThrow { underTest.run(mockk<ApplicationArguments>()) }

        // Assert
        verifySequence {
            keyService.hasKeyPair()
            keyService.hasRepositoryKey()
            registrationService.registerWithRepository(body)
        }
    }
}
*/