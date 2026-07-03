package org.edu_sharing.rendering.modules.eduhtml

import org.edu_sharing.generated.repository.backend.services.rest.client.model.Node
import org.edu_sharing.rendering.core.dto.ObjectLink
import org.edu_sharing.rendering.core.dto.RenderDataResponse
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.core.exception.ResourceNotFoundException
import org.edu_sharing.rendering.edusharingRepo.services.RepositoryRegistrationStorageService
import org.edu_sharing.rendering.modules.RenderModule
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class EduHtmlRenderModule(
    @Value($$"${app.session.eduHtml.nodePermissionExpirationTime}")
    private val nodePermissionExpirationTime: Long?,
    private val eduHtmlService: EduHtmlService,
    private val mapper: Mapper,
    private val repositoryRegistrationStorageService: RepositoryRegistrationStorageService
) : RenderModule {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun module() = "EDUHTML"
    override fun isOptionalModule() = true

    override fun handle(node: Node): RenderDataResponse {
        log.debug("Handling EduHTML node ${node.ref.id}, checking cache")
        val staticLink = try {
            val cacheObject = mapper.nodeToCacheObject(node)
            val candidates = eduHtmlService.entryCandidates(eduHtmlService.resolveMainEntity(node))
            eduHtmlService.getObjectLink(cacheObject, candidates)
        } catch (_: ResourceNotFoundException) {
            null
        }

        if (staticLink == null) {
            log.debug("EduHTML node ${node.ref.id} not cached, creating async job")
            return RenderDataResponse(
                module = module(),
                objectLinks = mutableListOf(),
                jobId = eduHtmlService.createJob(node, module())
            )
        }

        log.debug("EduHTML node ${node.ref.id} found in cache, returning immediate link")
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
