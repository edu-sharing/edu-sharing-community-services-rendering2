package org.edu_sharing.rendering.security.jwt

import io.jsonwebtoken.*
import org.edu_sharing.rendering.service.PrivatePublicKeyService
import org.slf4j.LoggerFactory
import org.springframework.security.core.GrantedAuthority

class JwtUtils(private var keyService: PrivatePublicKeyService) {

    private val log = LoggerFactory.getLogger(javaClass)
    private fun getJwtParser() : JwtParser {
        return Jwts.parser()
            .verifyWith(keyService.getRepoPublicKey())
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

    @Suppress("UNCHECKED_CAST")
    fun getUserDetailsFromJwt(jwt: String): JWTBasedUserDetail {
        val jwtObj = getJwtParser().parseSignedClaims(jwt)

        val grantedAuthority = mutableListOf<GrantedAuthority>()

        return JWTBasedUserDetail(
            jwtObj.payload.issuer,
            jwtObj.payload.get("node", String::class.java),
            jwtObj.payload.notBefore,
            jwtObj.payload.expiration,
            grantedAuthority,
            jwtObj.payload.get("permissions", List::class.java) as MutableCollection<String>,
        )
    }
}