package org.edu_sharing.rendering.security.jwt

import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtParser
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.MalformedJwtException
import io.jsonwebtoken.UnsupportedJwtException
import org.slf4j.LoggerFactory
import org.springframework.security.core.GrantedAuthority
import java.security.PublicKey

class JwtUtils(publicKey: PublicKey) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val jwtParser: JwtParser = Jwts.parser()
        .verifyWith(publicKey)
        .build()

    fun validateJwtToken(jwt: String): Boolean {
        try {
            jwtParser.parse(jwt)
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
        val jwtObj = jwtParser.parseSignedClaims(jwt)

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