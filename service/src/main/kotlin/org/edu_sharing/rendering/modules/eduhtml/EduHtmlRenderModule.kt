package org.edu_sharing.rendering.modules.eduhtml

import io.minio.errors.ErrorResponseException
import org.edu_sharing.rendering.dto.ObjectLink
import org.edu_sharing.rendering.dto.RenderDataRequest
import org.edu_sharing.rendering.dto.RenderDataResponse
import org.edu_sharing.rendering.dto.RenderModules
import org.edu_sharing.rendering.entity.RenderingJob
import org.edu_sharing.rendering.entity.SubJob
import org.edu_sharing.rendering.modules.RenderModule
import org.springframework.stereotype.Component

@Component
class EduHtmlRenderModule(private val eduHtmlService: EduHtmlService) : RenderModule {
    override fun module() = RenderModules.EDUHTML

    override fun handle(request: RenderDataRequest) : RenderDataResponse {
        val staticLink = try {
            eduHtmlService.getObjectLink(request.nodeId)
        } catch (_: ErrorResponseException) {
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
}
