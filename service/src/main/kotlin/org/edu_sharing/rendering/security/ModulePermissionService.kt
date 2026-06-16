package org.edu_sharing.rendering.security

import org.edu_sharing.rendering.edusharingRepo.services.RepositoryRegistrationStorageService
import org.edu_sharing.rendering.modules.RenderModule
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class ModulePermissionService (
    private val nodePermissionSessionContextRepository: NodePermissionSessionContextRepository,
    private val repositoryRegistrationStorageService: RepositoryRegistrationStorageService,
    @Value($$"${app.security.enabled}")
    private val securityEnabled: Boolean
){
    private val log = LoggerFactory.getLogger(javaClass)

    fun hasModuleAccess(renderModule: RenderModule, nodeId:String): Boolean {
        if (!securityEnabled) {
            log.debug("Module access check skipped (security disabled): module=${renderModule.module()}, nodeId=$nodeId")
            return true
        }
        val nodePermission = nodePermissionSessionContextRepository.getNodePermission(nodeId)
        val result = nodePermission != null && hasModuleAccessInternal(renderModule, nodePermission.repoId)
        log.debug("Module access check: module=${renderModule.module()}, nodeId=$nodeId, result=${if (result) "allowed" else "denied"}")
        return result
    }

    private fun hasModuleAccessInternal(renderModule: RenderModule, repoId: String): Boolean {

        if(!renderModule.isOptionalModule()){
            return true
        }

        val optionalModules = repositoryRegistrationStorageService.getRegistrationByRepoId(repoId)
            .map { it.optionalModules }
            .orElseGet {mutableListOf()}
        val allowed = optionalModules.contains(renderModule.module())
        log.debug("Optional module access for module=${renderModule.module()}, repoId=$repoId: ${if (allowed) "allowed" else "denied"}")
        return allowed
    }
}
