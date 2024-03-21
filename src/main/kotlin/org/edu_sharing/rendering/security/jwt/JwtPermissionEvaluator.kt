package org.edu_sharing.rendering.security.jwt

import org.springframework.security.access.PermissionEvaluator
import org.springframework.security.core.Authentication
import java.io.Serializable

class JwtPermissionEvaluator : PermissionEvaluator {
    override fun hasPermission(authentication: Authentication?, targetDomainObject: Any?, permission: Any?): Boolean {
        if(authentication == null){
            return false
        }

        val principal = (authentication.principal as JWTBasedUserDetail)
        return principal.node == targetDomainObject && principal.permissions.stream().anyMatch{ it == permission }
    }

    override fun hasPermission(
        authentication: Authentication?,
        targetId: Serializable?,
        targetType: String?,
        permission: Any?
    ): Boolean {
        if(authentication == null){
            return false
        }

        val principal = (authentication.principal as JWTBasedUserDetail)
        return principal.node == targetId && principal.permissions.stream().anyMatch{ it == permission }
    }

}
