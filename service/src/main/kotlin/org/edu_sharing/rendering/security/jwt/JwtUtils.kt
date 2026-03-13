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

        @Suppress("UNCHECKED_CAST")
        override fun onVerifiedClaims(jws: Jws<Claims>?): NodePermission? {
            if (jws != null) {
                return NodePermission(
                    repoId = jws.payload.get("repoId", String::class.java),
                    nodeId = jws.payload.get("node", String::class.java),
                    permissions = (jws.payload.get("permissions", List::class.java) as Collection<String>).toSet(),
                    lastAccessDate = LocalDateTime.now()
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
                    username = jws.payload.issuer,
                    notBefore =jws.payload.notBefore,
                    expirationDate = jws.payload.expiration,
                    authorities = grantedAuthority,
                    repoId = jws.payload.get("repoId", String::class.java),
                    firstName = jws.payload.get("firstName", String::class.java),
                    lastName = jws.payload.get("lastName", String::class.java),
                    email = jws.payload.get("userEmail", String::class.java),
                    primaryAffiliation = jws.payload.getOrDefault("primaryAffiliation", "") as String
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
            log.error("Invalid JWT token: {}", e.message, e)
        } catch (e: ExpiredJwtException) {
            log.error("JWT token is expired: {}", e.message, e)
        } catch (e: UnsupportedJwtException) {
            log.error("JWT token is unsupported: {}", e.message, e)
        } catch (e: IllegalArgumentException) {
            log.error("JWT claims string is empty: {}", e.message, e)
        } catch (e: InvalidKeyException) {
            log.error("JWT parser has no valid public key: {}", e.message, e)
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
