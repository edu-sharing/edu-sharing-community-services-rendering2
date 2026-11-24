package org.edu_sharing.rendering.edusharingRepo.services

import io.mockk.every
import io.mockk.mockk
import org.edu_sharing.rendering.edusharingRepo.services.RepositoryAppAssertionVerifier.InvalidAppAssertionException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

class RepositoryAppAssertionVerifierTest {

    private val tokens = AppAssertionTestTokens()
    private val keyService = mockk<RepositoryPublicKeyService>().also {
        every { it.getRepositoryKey("local") } returns tokens.keys.public
        every { it.getRepositoryKey("unknown") } throws IllegalArgumentException("Repository not found for id: unknown")
    }
    private val verifier = RepositoryAppAssertionVerifier(
        keyService,
        RepositorySignatureVerifier(keyService, listOf("SHA256withRSA", "SHA384withRSA", "SHA512withRSA")),
        AppAssertionProperties(),
    )
    private val now = Instant.ofEpochSecond(1_800_000_000)

    @Test
    fun `accepts a valid assertion and returns the issuer`() {
        // Arrange
        val assertion = tokens.create(now = now)

        // Act
        val issuer = verifier.verify(assertion, now)

        // Assert
        assertEquals("local", issuer)
    }

    @Test
    fun `accepts RS384 and RS512`() {
        // Arrange
        val rs384 = tokens.create(header = mapOf("alg" to "RS384"), signatureAlgorithm = "SHA384withRSA", now = now)
        val rs512 = tokens.create(header = mapOf("alg" to "RS512"), signatureAlgorithm = "SHA512withRSA", now = now)

        // Act
        val issuers = listOf(verifier.verify(rs384, now), verifier.verify(rs512, now))

        // Assert
        assertEquals(listOf("local", "local"), issuers)
    }

    @Test
    fun `accepts aud as an array`() {
        // Arrange
        val assertion = tokens.create(claims = mapOf("aud" to listOf("other", "rendering-service")), now = now)

        // Act
        val issuer = verifier.verify(assertion, now)

        // Assert
        assertEquals("local", issuer)
    }

    @Test
    fun `accepts a key below 2048 bits`() {
        // Arrange
        val smallKeyTokens = AppAssertionTestTokens(AppAssertionTestTokens.generateKeyPair(1024))
        every { keyService.getRepositoryKey("small") } returns smallKeyTokens.keys.public
        val assertion = smallKeyTokens.create(claims = mapOf("iss" to "small"), now = now)

        // Act
        val issuer = verifier.verify(assertion, now)

        // Assert
        assertEquals("small", issuer)
    }

    @Test
    fun `rejects a missing or malformed assertion`() {
        // Arrange
        val assertions = listOf(null, "", "a.b", "not.a.jws", "e30.e30.")

        // Act & Assert
        assertions.forEach { assertion ->
            assertThrows<InvalidAppAssertionException>("for '$assertion'") { verifier.verify(assertion, now) }
        }
    }

    @Test
    fun `rejects a signature made with another key`() {
        // Arrange
        val assertion = tokens.create(signingKey = AppAssertionTestTokens.generateKeyPair().private, now = now)

        // Act & Assert
        assertThrows<InvalidAppAssertionException> { verifier.verify(assertion, now) }
    }

    @Test
    fun `rejects a modified payload`() {
        // Arrange
        val original = tokens.create(now = now).split('.')
        val other = tokens.create(claims = mapOf("jti" to "another-id"), now = now).split('.')
        val tampered = listOf(original[0], other[1], original[2]).joinToString(".")

        // Act & Assert
        assertThrows<InvalidAppAssertionException> { verifier.verify(tampered, now) }
    }

    @Test
    fun `rejects an unknown repository`() {
        // Arrange
        val assertion = tokens.create(claims = mapOf("iss" to "unknown"), now = now)

        // Act & Assert
        assertThrows<InvalidAppAssertionException> { verifier.verify(assertion, now) }
    }

    @Test
    fun `rejects a missing issuer`() {
        // Arrange
        val assertion = tokens.create(claims = mapOf("iss" to null), now = now)

        // Act & Assert
        assertThrows<InvalidAppAssertionException> { verifier.verify(assertion, now) }
    }

    @Test
    fun `rejects a wrong or missing typ`() {
        // Arrange
        val wrongTyp = tokens.create(header = mapOf("typ" to "bapi-user+jwt"), now = now)
        val missingTyp = tokens.create(header = mapOf("typ" to null), now = now)

        // Act & Assert
        assertThrows<InvalidAppAssertionException> { verifier.verify(wrongTyp, now) }
        assertThrows<InvalidAppAssertionException> { verifier.verify(missingTyp, now) }
    }

    @Test
    fun `rejects an algorithm that is not supported`() {
        // Arrange
        val none = tokens.create(header = mapOf("alg" to "none"), now = now)
        val hmac = tokens.create(header = mapOf("alg" to "HS256"), now = now)

        // Act & Assert
        assertThrows<InvalidAppAssertionException> { verifier.verify(none, now) }
        assertThrows<InvalidAppAssertionException> { verifier.verify(hmac, now) }
    }

    @Test
    fun `rejects an algorithm that is not whitelisted`() {
        // Arrange
        val strictVerifier = RepositoryAppAssertionVerifier(
            keyService, RepositorySignatureVerifier(keyService, listOf("SHA512withRSA")), AppAssertionProperties()
        )
        val assertion = tokens.create(now = now)

        // Act & Assert
        assertThrows<InvalidAppAssertionException> { strictVerifier.verify(assertion, now) }
    }

    @Test
    fun `rejects a kid that does not match the key of the registration`() {
        // Arrange
        val assertion = tokens.create(header = mapOf("kid" to "another-thumbprint"), now = now)

        // Act & Assert
        assertThrows<InvalidAppAssertionException> { verifier.verify(assertion, now) }
    }

    @Test
    fun `rejects a crit header`() {
        // Arrange
        val assertion = tokens.create(header = mapOf("crit" to listOf("exp")), now = now)

        // Act & Assert
        assertThrows<InvalidAppAssertionException> { verifier.verify(assertion, now) }
    }

    @Test
    fun `rejects an assertion for another audience`() {
        // Arrange
        val other = tokens.create(claims = mapOf("aud" to "bildungs-api"), now = now)
        val missing = tokens.create(claims = mapOf("aud" to null), now = now)

        // Act & Assert
        assertThrows<InvalidAppAssertionException> { verifier.verify(other, now) }
        assertThrows<InvalidAppAssertionException> { verifier.verify(missing, now) }
    }

    @Test
    fun `rejects an expired assertion beyond the clock skew`() {
        // Arrange
        val assertion = tokens.create(now = now.minusSeconds(41))

        // Act & Assert
        assertThrows<InvalidAppAssertionException> { verifier.verify(assertion, now) }
    }

    @Test
    fun `accepts an expired assertion within the clock skew`() {
        // Arrange
        val assertion = tokens.create(now = now.minusSeconds(39))

        // Act
        val issuer = verifier.verify(assertion, now)

        // Assert
        assertEquals("local", issuer)
    }

    @Test
    fun `rejects an assertion issued in the future`() {
        // Arrange
        val assertion = tokens.create(now = now.plusSeconds(11))

        // Act & Assert
        assertThrows<InvalidAppAssertionException> { verifier.verify(assertion, now) }
    }

    @Test
    fun `rejects a lifetime that is too long or not positive`() {
        // Arrange
        val tooLong = tokens.create(claims = mapOf("exp" to now.epochSecond + 3600), now = now)
        val notPositive = tokens.create(claims = mapOf("exp" to now.epochSecond), now = now)

        // Act & Assert
        assertThrows<InvalidAppAssertionException> { verifier.verify(tooLong, now) }
        assertThrows<InvalidAppAssertionException> { verifier.verify(notPositive, now) }
    }

    @Test
    fun `rejects a missing or non-numeric iat or exp`() {
        // Arrange
        val missingExp = tokens.create(claims = mapOf("exp" to null), now = now)
        val textIat = tokens.create(claims = mapOf("iat" to now.epochSecond.toString()), now = now)

        // Act & Assert
        assertThrows<InvalidAppAssertionException> { verifier.verify(missingExp, now) }
        assertThrows<InvalidAppAssertionException> { verifier.verify(textIat, now) }
    }
}
