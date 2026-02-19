package org.edu_sharing.rendering.modules.eduhtml

import org.edu_sharing.generated.repository.backend.services.rest.client.model.Node
import org.edu_sharing.rendering.core.dto.ObjectLink
import org.edu_sharing.rendering.core.dto.RenderDataResponse
import org.edu_sharing.rendering.core.dto.RequestUserData
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.core.exception.ResourceNotFoundException
import org.edu_sharing.rendering.edusharingRepo.services.RepositoryRegistrationStorageService
import org.edu_sharing.rendering.modules.RenderModule
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class EduHtmlRenderModule(
    @Value("\${app.session.eduHtml.nodePermissionExpirationTime}")
    private val nodePermissionExpirationTime: Long?,
    private val eduHtmlService: EduHtmlService,
    private val mapper: Mapper,
    private val repositoryRegistrationStorageService: RepositoryRegistrationStorageService
) : RenderModule {
    override fun module() = "EDUHTML"
    override fun isOptionalModule() = true

    override fun handle(node: Node, userData: RequestUserData): RenderDataResponse {
        val staticLink = try {
            val cacheObject = mapper.nodeToCacheObject(node)
            eduHtmlService.getObjectLink(cacheObject)
        } catch (_: ResourceNotFoundException) {
            null
        }

        if (staticLink == null) {
            return RenderDataResponse(
                module = module(),
                objectLinks = mutableListOf(),
                jobId = eduHtmlService.createJob(node, module())
            )
        }

        return RenderDataResponse(
            module = module(),
            objectLinks = mutableListOf(staticLink),
            jobId = null
        )
    }

    override fun getObjectLinkFromJobData(subJob: SubJob, renderingJob: RenderingJob): ObjectLink? {
        return ObjectLink(link = subJob.message ?: "")
    }

    override fun getNodePermissionExpirationTime() = nodePermissionExpirationTime
    override fun getCspHeader(repoId: String): String? {
        val registration = repositoryRegistrationStorageService.getRegistrationByRepoId(repoId)
            .orElseThrow { IllegalArgumentException("Unknown repository id: $repoId") }
        return registration.module[module()]?.cspHeader
    }
}
