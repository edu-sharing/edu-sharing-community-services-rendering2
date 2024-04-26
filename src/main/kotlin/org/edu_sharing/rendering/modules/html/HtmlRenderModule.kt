package org.edu_sharing.rendering.modules.html

import org.edu_sharing.rendering.dto.RenderDataRequest
import org.edu_sharing.rendering.dto.RenderDataResponse
import org.edu_sharing.rendering.dto.RenderModules
import org.edu_sharing.rendering.modules.RenderModule
import org.edu_sharing.rendering.service.EduHtmlService
import org.springframework.stereotype.Component

@Component
class HtmlRenderModule(private val eduHtmlService: EduHtmlService) : RenderModule {
    override fun module() = RenderModules.EDUHTML

    override fun handle(request: RenderDataRequest) : RenderDataResponse {
        val staticLink = eduHtmlService.getObjectLink(request.nodeId)

        return RenderDataResponse(
            module = module(),
            objectLinks =  if (staticLink != null) mutableListOf(staticLink) else mutableListOf(),
            jobId = if (staticLink != null) null else eduHtmlService.createJob(request))
    }
}
