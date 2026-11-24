package org.edu_sharing.rendering.edusharingRepo.services

import tools.jackson.databind.json.JsonMapper
import java.nio.charset.StandardCharsets
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Signature
import java.time.Instant
import java.util.Base64

/**
 * Builds app assertions the way the repository does (compact JWS, see [RepositoryAppAssertionVerifier]).
 * Entries in `header`/`claims` replace the defaults; a value of null removes the entry.
 */
class AppAssertionTestTokens(val keys: KeyPair = generateKeyPair()) {

    fun create(
        header: Map<String, Any?> = emptyMap(),
        claims: Map<String, Any?> = emptyMap(),
        signingKey: PrivateKey = keys.private,
        signatureAlgorithm: String = "SHA256withRSA",
        now: Instant = Instant.now(),
    ): String {
        val fullHeader = (mapOf(
            "alg" to "RS256",
            "typ" to RepositoryAppAssertionVerifier.TYP,
            "kid" to RepositoryAppAssertionVerifier.thumbprint(keys.public),
        ) + header).filterValues { it != null }
        val fullClaims = (mapOf(
            "iss" to "local",
            "aud" to "rendering-service",
            "iat" to now.epochSecond,
            "exp" to now.epochSecond + 30,
            "jti" to "a-unique-id",
        ) + claims).filterValues { it != null }
        val signingInput = encode(fullHeader) + "." + encode(fullClaims)
        val signature = Signature.getInstance(signatureAlgorithm).run {
            initSign(signingKey)
            update(signingInput.toByteArray(StandardCharsets.US_ASCII))
            sign()
        }
        return signingInput + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signature)
    }

    private fun encode(part: Map<String, Any?>): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(JSON.writeValueAsBytes(part))

    companion object {
        private val JSON: JsonMapper = JsonMapper.builder().build()

        fun generateKeyPair(bits: Int = 2048): KeyPair =
            KeyPairGenerator.getInstance("RSA").apply { initialize(bits) }.generateKeyPair()
    }
}
