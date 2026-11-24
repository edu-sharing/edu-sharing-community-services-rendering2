package org.edu_sharing.rendering.edusharingRepo.services

import org.edu_sharing.rendering.core.annotation.ConditionalOnController
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.PublicKey
import java.security.interfaces.RSAPublicKey
import java.time.Instant
import java.util.Base64

/**
 * Verifies the app assertion of a registered repository: a compact JWS the repository signs with its key to call this
 * service as itself (no user), sent in the header [HEADER].
 *
 * - JOSE header: `alg` (`RS256`/`RS384`/`RS512`), `typ` = [TYP], `kid` = RFC 7638 thumbprint of the repository's public
 *   key. A `crit` header is refused.
 * - Claims: `iss` (app id of the repository = its repoId here), `aud` ([AppAssertionProperties.audience]), `iat`, `exp`
 *   (at most [AppAssertionProperties.maxLifetime] after `iat`), `jti`.
 *
 * The key is the one of the registration (`iss`), the signature is checked by [RepositorySignatureVerifier], so the
 * algorithm whitelist `app.security.allowed-signature-algorithms` applies. Verified by hand instead of with jjwt, because
 * jjwt refuses RSA keys below 2048 bits, which older repositories may still have.
 * `jti` is not checked against replays: the assertion is short-lived and only used for read-only endpoints.
 */
@Component
@ConditionalOnController
class RepositoryAppAssertionVerifier(
    private val repositoryPublicKeyService: RepositoryPublicKeyService,
    private val repositorySignatureVerifier: RepositorySignatureVerifier,
    private val properties: AppAssertionProperties,
) {

    class InvalidAppAssertionException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

    /**
     * @return the app id (`iss`) of the repository that signed the assertion
     * @throws InvalidAppAssertionException if the assertion is missing, malformed, expired or not signed by a registered repository
     */
    fun verify(assertion: String?, now: Instant = Instant.now()): String {
        if (assertion.isNullOrBlank()) {
            throw InvalidAppAssertionException("missing assertion")
        }
        val parts = assertion.split('.')
        if (parts.size != 3) {
            throw InvalidAppAssertionException("not a compact JWS")
        }
        val header = decodeJson(parts[0], "header")
        val claims = decodeJson(parts[1], "claims")

        if (header.path("typ").asString(null) != TYP) {
            throw InvalidAppAssertionException("unexpected typ")
        }
        if (header.has("crit")) {
            throw InvalidAppAssertionException("crit header is not supported")
        }
        val algorithm = JWS_ALGORITHMS[header.path("alg").asString(null)]
            ?: throw InvalidAppAssertionException("unsupported alg")
        val issuer = claims.path("iss").asString(null)
        if (issuer.isNullOrBlank()) {
            throw InvalidAppAssertionException("missing iss")
        }

        val signature = try {
            Base64.getUrlDecoder().decode(parts[2])
        } catch (e: IllegalArgumentException) {
            throw InvalidAppAssertionException("signature is no Base64url", e)
        }
        try {
            repositorySignatureVerifier.verify(
                issuer, (parts[0] + "." + parts[1]).toByteArray(StandardCharsets.US_ASCII), signature, algorithm
            )
        } catch (e: Exception) {
            // algorithm not allowed, unknown repository, broken key or signature
            throw InvalidAppAssertionException("signature could not be verified: ${e.message}", e)
        }

        if (header.path("kid").asString(null) != thumbprint(repositoryPublicKeyService.getRepositoryKey(issuer))) {
            throw InvalidAppAssertionException("kid does not match the key of the registration")
        }
        verifyAudience(claims.path("aud"))
        verifyLifetime(claims, now)
        return issuer
    }

    private fun verifyAudience(aud: JsonNode) {
        val audiences = if (aud.isArray) aud.values().map { it.asString(null) } else listOf(aud.asString(null))
        if (properties.audience !in audiences) {
            throw InvalidAppAssertionException("unexpected aud")
        }
    }

    private fun verifyLifetime(claims: JsonNode, now: Instant) {
        val iat = claims.path("iat")
        val exp = claims.path("exp")
        if (!iat.isIntegralNumber || !exp.isIntegralNumber) {
            throw InvalidAppAssertionException("missing iat or exp")
        }
        val issuedAt = iat.asLong()
        val expiresAt = exp.asLong()
        val nowSeconds = now.epochSecond
        val skew = properties.clockSkew.seconds
        if (expiresAt <= issuedAt || expiresAt - issuedAt > properties.maxLifetime.seconds) {
            throw InvalidAppAssertionException("lifetime not accepted")
        }
        if (issuedAt > nowSeconds + skew) {
            throw InvalidAppAssertionException("issued in the future")
        }
        if (expiresAt < nowSeconds - skew) {
            throw InvalidAppAssertionException("expired")
        }
    }

    private fun decodeJson(part: String, name: String): JsonNode =
        try {
            JSON.readTree(Base64.getUrlDecoder().decode(part)).also {
                if (!it.isObject) throw InvalidAppAssertionException("$name is no JSON object")
            }
        } catch (e: InvalidAppAssertionException) {
            throw e
        } catch (e: Exception) {
            throw InvalidAppAssertionException("$name could not be read", e)
        }

    companion object {
        const val HEADER = "X-Edu-App-Assertion"
        const val TYP = "edu-app+jwt"

        private val JSON: JsonMapper = JsonMapper.builder().build()

        private val JWS_ALGORITHMS = mapOf(
            "RS256" to "SHA256withRSA",
            "RS384" to "SHA384withRSA",
            "RS512" to "SHA512withRSA",
        )

        /** RFC 7638 thumbprint (SHA-256, Base64url) of an RSA public key, as the repository names it in `kid`. */
        fun thumbprint(key: PublicKey): String? {
            if (key !is RSAPublicKey) {
                return null
            }
            // the members in lexicographic order, no blanks, as RFC 7638 says
            val json = """{"e":"${unsigned(key.publicExponent)}","kty":"RSA","n":"${unsigned(key.modulus)}"}"""
            return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(MessageDigest.getInstance("SHA-256").digest(json.toByteArray(StandardCharsets.UTF_8)))
        }

        private fun unsigned(value: BigInteger): String {
            val bytes = value.toByteArray()
            val start = if (bytes.size > 1 && bytes[0] == 0.toByte()) 1 else 0
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.copyOfRange(start, bytes.size))
        }
    }
}
