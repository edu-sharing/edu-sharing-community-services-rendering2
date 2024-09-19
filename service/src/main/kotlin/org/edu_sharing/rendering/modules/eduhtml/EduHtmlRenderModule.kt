package org.edu_sharing.rendering.modules.eduhtml

import org.edu_sharing.rendering.dto.ObjectLink
import org.edu_sharing.rendering.dto.RenderDataRequest
import org.edu_sharing.rendering.dto.RenderDataResponse
import org.edu_sharing.rendering.dto.mapper.Mapper
import org.edu_sharing.rendering.renderingJobs.entity.RenderingJob
import org.edu_sharing.rendering.renderingJobs.entity.SubJob
import org.edu_sharing.rendering.exception.ResourceNotFoundException
import org.edu_sharing.rendering.modules.RenderModule
import org.edu_sharing.rendering.modules.RenderModules
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class EduHtmlRenderModule(
    @Value("\${app.session.eduHtml.nodePermissionExpirationTime}")
    private val nodePermissionExpirationTime: Long?,
    private val eduHtmlService: EduHtmlService,
    private val mapper: Mapper
) : RenderModule {
    override fun module() = RenderModules.EDUHTML

    override fun handle(request: RenderDataRequest): RenderDataResponse {
        val staticLink = try {
            val cacheObject = mapper.renderDataRequestToCacheObject(request)
            eduHtmlService.getObjectLink(cacheObject)
        } catch (_: ResourceNotFoundException) {
            null
        }

        if (staticLink == null) {
            return RenderDataResponse(
                module = module(),
                objectLinks = mutableListOf(),
                jobId = eduHtmlService.createJob(request, module())
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
}
