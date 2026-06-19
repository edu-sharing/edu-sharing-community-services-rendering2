package org.edu_sharing.rendering.security

import org.springframework.security.access.PermissionEvaluator
import org.springframework.security.core.Authentication
import java.io.Serializable


class AllowAllPermissionEvaluator : PermissionEvaluator {

    override fun hasPermission(authentication: Authentication, targetDomainObject: Any, permission: Any): Boolean {
        return true
    }

    override fun hasPermission(
        authentication: Authentication,
        targetId: Serializable,
        targetType: String,
        permission: Any
    ): Boolean {
        return true
    }
}
