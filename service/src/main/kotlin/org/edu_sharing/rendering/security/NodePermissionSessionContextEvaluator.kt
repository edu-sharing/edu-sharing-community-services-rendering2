package org.edu_sharing.rendering.security

import org.springframework.security.access.PermissionEvaluator
import org.springframework.security.core.Authentication
import java.io.Serializable


class NodePermissionSessionContextEvaluator(private val nodePermissionSessionContextRepository: NodePermissionSessionContextRepository) : PermissionEvaluator {

    override fun hasPermission(authentication: Authentication, targetDomainObject: Any, permission: Any): Boolean {
        if(targetDomainObject !is String){
            throw IllegalArgumentException("targetDomainObject must be a String")
        }

        if(permission !is String){
            throw IllegalArgumentException("permission must be a String")
        }

        return nodePermissionSessionContextRepository.hasPermission(targetDomainObject, permission)
    }

    override fun hasPermission(
        authentication: Authentication,
        targetId: Serializable,
        targetType: String,
        permission: Any
    ): Boolean {
        if(targetId !is String){
            throw IllegalArgumentException("targetId must be a String")
        }

        if(permission !is String){
            throw IllegalArgumentException("permission must be a String")
        }

        return nodePermissionSessionContextRepository.hasPermission(targetId, permission)
    }
}
