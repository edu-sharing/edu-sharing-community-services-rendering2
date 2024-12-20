package org.edu_sharing.rendering.modules.binder

import org.edu_sharing.rendering.core.dto.RenderDataRequest
import org.edu_sharing.rendering.core.dto.RenderDataResponse
import org.edu_sharing.rendering.edusharingRepo.services.RepositoryRegistrationStorageService
import org.edu_sharing.rendering.modules.RenderModule
import org.edu_sharing.rendering.modules.ThirdPartyModule
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class BinderRenderModule(
    private val jobService: BinderJobService,
    private val repositoryRegistrationStorageService: RepositoryRegistrationStorageService,
    @Value("\${app.session.moodle.nodePermissionExpirationTime}")
    private val nodePermissionExpirationTime: Long?,
    ): RenderModule, ThirdPartyModule {

    companion object {
        private val requiredCredentialKeys = setOf("baseurl")
    }

    override fun module() = "BINDER"

    override fun handle(request: RenderDataRequest): RenderDataResponse {
        val jobId = jobService.createJob(request, module())
        return RenderDataResponse(jobId = jobId)
    }

    override fun isOptionalModule() = true

    override fun getNodePermissionExpirationTime() = nodePermissionExpirationTime

    override fun validateThirdPartyCredentials(
        credentials: Map<String, String>,
        repoId: String
    ) {
        validateCredentials(
            credentials = credentials,
            requiredCredentialKeys = requiredCredentialKeys,
            moduleName = module()
        )
    }

    override fun getConfig(repoId: String): Map<String, String> {
        val registration = repositoryRegistrationStorageService.getRegistrationByRepoId(repoId)
            .orElseThrow { IllegalArgumentException("Unknown repository id: $repoId") }
        return registration.module[module()]?.credentials ?: mapOf()
    }
}