package org.edu_sharing.rendering.security.jwt

import io.jsonwebtoken.*
import org.edu_sharing.rendering.edusharingRepo.services.RepositoryPublicKeyService
import org.edu_sharing.rendering.security.NodePermission
import org.slf4j.LoggerFactory
import org.springframework.security.core.GrantedAuthority
import org.springframework.stereotype.Component
import java.security.InvalidKeyException
import java.security.Key
import java.time.LocalDateTime


@Component
class JwtUtils(private val repositoryPublicKeyService: RepositoryPublicKeyService) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val jwtParser = Jwts.parser().keyLocator(JWTKeyLocator()).build()

    inner class JWTKeyLocator : Locator<Key> {

        override fun locate(header: Header?): Key? {
            if (header == null) {
                return null
            }

            val repoId = header["repoId"].toString()
            return repositoryPublicKeyService.getRepositoryKey(repoId)
        }
    }

    private class JWTNodePermissionResolver : SupportedJwtVisitor<NodePermission>() {

        override fun onVerifiedClaims(jws: Jws<Claims>?): NodePermission? {
            if (jws != null) {
                return NodePermission(
                    jws.payload.get("repoId", String::class.java),
                    jws.payload.get("node", String::class.java),
                    (jws.payload.get("permissions", List::class.java) as Collection<String>).toSet(),
                    jws.payload.getOrElse("mimeType") { "text/x-uri" }.toString(),
                    jws.payload.get("mediaType", String::class.java),
                    jws.payload.getOrElse("replicationSource") { "" }.toString(),
                    jws.payload.getOrElse("resourceType") { "" }.toString(),
                    LocalDateTime.now()
                )
            }
            return null;
        }
    }

    private class JWTUserDetailsResolver : SupportedJwtVisitor<JWTBasedUserDetail>() {

        override fun onVerifiedClaims(jws: Jws<Claims>?): JWTBasedUserDetail? {
            if (jws != null) {
                val grantedAuthority = mutableListOf<GrantedAuthority>()
                return JWTBasedUserDetail(
                    jws.payload.issuer,
                    jws.payload.notBefore,
                    jws.payload.expiration,
                    grantedAuthority,
                    jws.payload.get("repoId", String::class.java),
                )
            }
            return null;
        }
    }


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
        return jwtParser.parse(jwt).accept(JWTUserDetailsResolver())
    }

    fun getNodePermissions(jwt: String): NodePermission {
        return jwtParser.parse(jwt).accept(JWTNodePermissionResolver())
    }
}
