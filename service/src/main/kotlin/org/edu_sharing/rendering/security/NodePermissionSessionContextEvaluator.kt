package org.edu_sharing.rendering.security

import org.slf4j.LoggerFactory
import org.springframework.security.access.PermissionEvaluator
import org.springframework.security.core.Authentication
import java.io.Serializable


class NodePermissionSessionContextEvaluator(private val nodePermissionSessionContextRepository: NodePermissionSessionContextRepository) : PermissionEvaluator {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun hasPermission(authentication: Authentication, targetDomainObject: Any, permission: Any): Boolean {
        if(targetDomainObject !is String){
            throw IllegalArgumentException("targetDomainObject must be a String")
        }

        if(permission !is String){
            throw IllegalArgumentException("permission must be a String")
        }

        val result = nodePermissionSessionContextRepository.hasPermission(targetDomainObject, permission)
        log.debug("PermissionEvaluator(object): nodeId=$targetDomainObject, permission=$permission, result=${if (result) "allowed" else "denied"}")
        return result
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

        val result = nodePermissionSessionContextRepository.hasPermission(targetId, permission)
        log.debug("PermissionEvaluator(id): nodeId=$targetId, targetType=$targetType, permission=$permission, result=${if (result) "allowed" else "denied"}")
        return result
    }
}
