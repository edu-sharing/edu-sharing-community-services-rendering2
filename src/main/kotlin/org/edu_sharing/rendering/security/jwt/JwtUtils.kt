package org.edu_sharing.rendering.security.jwt

import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtParser
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.MalformedJwtException
import io.jsonwebtoken.UnsupportedJwtException
import org.edu_sharing.rendering.security.NodePermission
import org.slf4j.LoggerFactory
import org.springframework.security.core.GrantedAuthority
import java.security.InvalidKeyException
import java.security.PublicKey
import java.time.LocalDateTime

class JwtUtils(private var publicKeyProvider: RepositoryPublicKeyProvider) {

    private val log = LoggerFactory.getLogger(javaClass)
    private fun getJwtParser(): JwtParser {
        if (publicKeyProvider.getPublicKey() == null) {
            throw InvalidKeyException("No public key available. Please register the application with edu-sharing repository first");
        }

        return Jwts.parser()
            .verifyWith(publicKeyProvider.getPublicKey())
            .build()
    }

    fun validateJwtToken(jwt: String): Boolean {
        try {
            getJwtParser().parse(jwt)
            return true
        } catch (e: MalformedJwtException) {
            log.error("Invalid JWT token: {}", e.message)
        } catch (e: ExpiredJwtException) {
            log.error("JWT token is expired: {}", e.message)
        } catch (e: UnsupportedJwtException) {
            log.error("JWT token is unsupported: {}", e.message)
        } catch (e: IllegalArgumentException) {
            log.error("JWT claims string is empty: {}", e.message)
        }
        return false
    }

    fun getUserDetailsFromJwt(jwt: String): JWTBasedUserDetail {
        val jwtObj = getJwtParser().parseSignedClaims(jwt)

        val grantedAuthority = mutableListOf<GrantedAuthority>()

        return JWTBasedUserDetail(
            jwtObj.payload.issuer,
            jwtObj.payload.notBefore,
            jwtObj.payload.expiration,
            grantedAuthority,
        )
    }

    @Suppress("UNCHECKED_CAST")
    fun getNodePermissions(jwt: String): NodePermission {
        val jwtObj = getJwtParser().parseSignedClaims(jwt)
        return NodePermission(
            jwtObj.payload.get("node", String::class.java),
            (jwtObj.payload.get("permissions", List::class.java) as Collection<String>).toSet(),
            LocalDateTime.now()
        )
    }
}