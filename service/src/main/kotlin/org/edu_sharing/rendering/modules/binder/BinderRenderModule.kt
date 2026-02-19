package org.edu_sharing.rendering.modules.binder

import org.edu_sharing.generated.repository.backend.services.rest.client.model.Node
import org.edu_sharing.rendering.core.dto.ObjectLink
import org.edu_sharing.rendering.core.dto.RenderDataResponse
import org.edu_sharing.rendering.core.dto.RequestUserData
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.edusharingRepo.services.RepositoryRegistrationStorageService
import org.edu_sharing.rendering.modules.RenderModule
import org.edu_sharing.rendering.modules.ThirdPartyModule
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.edu_sharing.rendering.storage.StorageService
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component

@Component
class BinderRenderModule(
    private val jobService: BinderService,
    private val repositoryRegistrationStorageService: RepositoryRegistrationStorageService,
    private val mapper: Mapper,
    private val storageService: StorageService,
    @Value("\${app.session.moodle.nodePermissionExpirationTime}")
    private val nodePermissionExpirationTime: Long?,
    ): RenderModule, ThirdPartyModule {

    companion object {
        private val requiredCredentialKeys = setOf("baseurl")
        private val optionalCredentialKeys = setOf("githubtoken")
    }

    override fun module() = "BINDER"

    override fun handle(node: Node, userData: RequestUserData): RenderDataResponse {
        val jobId = jobService.createJobs(node, this)
        return RenderDataResponse(
            jobId = jobId,
            module = module()
        )
    }

    override fun isOptionalModule() = true

    override fun getNodePermissionExpirationTime() = nodePermissionExpirationTime

    override fun validateThirdPartyCredentials(
        credentials: Map<String, String>,
        repoId: String
    ) {
        validateCredentials(
            credentials = credentials,
            requiredCredentialKeys = requiredCredentialKeys.filterNot { optionalCredentialKeys.contains(it) }.toSet(),
            moduleName = module()
        )
    }

    override fun getCredentials(repoId: String): Map<String, String> {
        val registration = repositoryRegistrationStorageService.getRegistrationByRepoId(repoId)
            .orElseThrow { IllegalArgumentException("Unknown repository id: $repoId") }
        return registration.module[module()]?.credentials ?: mapOf()
    }

    override fun getObjectLinkFromJobData(subJob: SubJob, renderingJob: RenderingJob): ObjectLink? {
        if (subJob.additionalData == null) {
            return ObjectLink(link = subJob.message ?: "")
        }
        val cacheObject = mapper.renderingJobToCacheObject(renderingJob)
        cacheObject.mimeType = MediaType.TEXT_HTML_VALUE
        return try {
            storageService.getObjectLink(cacheObject).first
        } catch (_: Exception) {
            null
        }
    }

    override fun getAdditionalDataFromSubJob(subJob: SubJob): Map<String, String>? {
        return subJob.additionalData
    }
}
