package org.edu_sharing.rendering.security

import org.edu_sharing.rendering.edusharingRepo.services.RepositoryRegistrationStorageService
import org.edu_sharing.rendering.modules.RenderModule
import org.edu_sharing.rendering.security.jwt.JWTBasedUserDetail
import org.springframework.security.access.expression.method.MethodSecurityExpressionOperations
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service

@Service
class ModulePermissionService (
    private val repositoryRegistrationStorageService: RepositoryRegistrationStorageService
){
    fun hasModuleAccess(operations: MethodSecurityExpressionOperations, renderModule: RenderModule): Boolean {
        val userDetails = operations.authentication.details as JWTBasedUserDetail
        return hasModuleAccess(renderModule, userDetails.repoId)
    }

    fun hasModuleAccess(renderModule: RenderModule): Boolean {
        val context = SecurityContextHolder.getContext()
        val userDetails = context.authentication.details as JWTBasedUserDetail
        return hasModuleAccess(renderModule, userDetails.repoId)
    }

    private fun hasModuleAccess(renderModule: RenderModule, repoId: String): Boolean {

        if(!renderModule.isOptionalModule()){
            return true
        }

        val optionalModules = repositoryRegistrationStorageService.getRegistrationByRepoId(repoId)
            .map { it.optionalModules }
            .orElseGet {mutableListOf()}
        return optionalModules.contains(renderModule.module())
    }
}
