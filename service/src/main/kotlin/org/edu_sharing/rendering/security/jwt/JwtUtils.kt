package org.edu_sharing.rendering.security.jwt

import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtParser
import io.jsonwebtoken.MalformedJwtException
import io.jsonwebtoken.UnsupportedJwtException
import org.edu_sharing.rendering.security.NodePermission
import org.slf4j.LoggerFactory
import org.springframework.security.core.GrantedAuthority
import java.security.InvalidKeyException
import java.time.LocalDateTime

class JwtUtils(private val jwtParser: JwtParser) {

    private val log = LoggerFactory.getLogger(javaClass)

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
        } catch (e: InvalidKeyException) {
            log.error("JWT parser has no valid public key: {}", e.message)
        }
        return false
    }

    fun getUserDetailsFromJwt(jwt: String): JWTBasedUserDetail {
        val jwtObj = jwtParser.parseSignedClaims(jwt)
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
        val jwtObj = jwtParser.parseSignedClaims(jwt)
        return NodePermission(
            jwtObj.payload.get("node", String::class.java),
            (jwtObj.payload.get("permissions", List::class.java) as Collection<String>).toSet(),
            jwtObj.payload.get("mimeType", String::class.java),
            jwtObj.payload.get("mediaType", String::class.java),
            LocalDateTime.now()
        )
    }
}
