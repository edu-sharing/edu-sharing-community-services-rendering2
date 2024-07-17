package org.edu_sharing.rendering.modules.eduhtml

import org.edu_sharing.rendering.dto.ObjectLink
import org.edu_sharing.rendering.dto.RenderDataRequest
import org.edu_sharing.rendering.dto.RenderDataResponse
import org.edu_sharing.rendering.dto.RenderModules
import org.edu_sharing.rendering.entity.RenderingJob
import org.edu_sharing.rendering.entity.SubJob
import org.edu_sharing.rendering.exception.ResourceNotFoundException
import org.edu_sharing.rendering.modules.RenderModule
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class EduHtmlRenderModule(
    @Value("\${app.session.eduHtml.nodePermissionExpirationTime}")
    private val nodePermissionExpirationTime: Long?,
    private val eduHtmlService: EduHtmlService
) : RenderModule {
    override fun module() = RenderModules.EDUHTML

    override fun handle(request: RenderDataRequest) : RenderDataResponse {
        val staticLink = try {
            eduHtmlService.getObjectLink(request.nodeId)
        } catch (_: ResourceNotFoundException) {
            null
        }

        return RenderDataResponse(
            module = module(),
            objectLinks = if (staticLink != null) mutableListOf(staticLink) else mutableListOf(),
            jobId = if (staticLink != null) null else eduHtmlService.createJob(request, module())
        )
    }

    override fun getObjectLinkFromJobData(subJob: SubJob, renderingJob: RenderingJob): ObjectLink? {
        return ObjectLink(link = subJob.message ?: "")
    }

    override fun getNodePermissionExpirationTime() = nodePermissionExpirationTime
}
