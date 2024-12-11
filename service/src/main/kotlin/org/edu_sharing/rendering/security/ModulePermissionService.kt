package org.edu_sharing.rendering.security

import org.edu_sharing.rendering.edusharingRepo.services.RepositoryRegistrationStorageService
import org.edu_sharing.rendering.security.jwt.JWTBasedUserDetail
import org.springframework.security.access.expression.method.MethodSecurityExpressionOperations
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service

@Service
class ModulePermissionService (
    private val repositoryRegistrationStorageService: RepositoryRegistrationStorageService
){
    fun hasModuleAccess(operations: MethodSecurityExpressionOperations): Boolean {

        val userDetails = operations.getAuthentication().details as JWTBasedUserDetail
        val optionalModules = repositoryRegistrationStorageService.getRegistrationByRepoId(userDetails.repoId)
            .map { it.optionalModules }
            .orElseGet {mutableListOf<String>()}
        return optionalModules.contains(operations.getFilterObject() as String)
    }

    fun hasModuleAccess2(module: String): Boolean {
        val context = SecurityContextHolder.getContext()
        val userDetails = context.authentication.details as JWTBasedUserDetail
        val optionalModules = repositoryRegistrationStorageService.getRegistrationByRepoId(userDetails.repoId)
            .map { it.optionalModules }
            .orElseGet {mutableListOf<String>()}
        return optionalModules.contains(module)
    }
}
