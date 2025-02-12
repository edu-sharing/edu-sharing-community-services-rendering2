package org.edu_sharing.rendering.security

import org.edu_sharing.rendering.edusharingRepo.services.RepositoryRegistrationStorageService
import org.edu_sharing.rendering.modules.RenderModule
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class ModulePermissionService (
    private val nodePermissionSessionContextRepository: NodePermissionSessionContextRepository,
    private val repositoryRegistrationStorageService: RepositoryRegistrationStorageService,
    @Value("\${app.security.enabled}")
    private val securityEnabled: Boolean
){
    fun hasModuleAccess(renderModule: RenderModule, nodeId:String): Boolean {
        if (!securityEnabled) {
            return true
        }
        val nodePermission = nodePermissionSessionContextRepository.getNodePermission(nodeId)
        return nodePermission != null && hasModuleAccessInternal(renderModule, nodePermission.repoId)
    }

    private fun hasModuleAccessInternal(renderModule: RenderModule, repoId: String): Boolean {

        if(!renderModule.isOptionalModule()){
            return true
        }

        val optionalModules = repositoryRegistrationStorageService.getRegistrationByRepoId(repoId)
            .map { it.optionalModules }
            .orElseGet {mutableListOf()}
        return optionalModules.contains(renderModule.module())
    }
}
