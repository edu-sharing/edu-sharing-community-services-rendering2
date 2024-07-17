package org.edu_sharing.rendering.modules.pdf

import org.edu_sharing.rendering.dto.ObjectLink
import org.edu_sharing.rendering.dto.RenderDataRequest
import org.edu_sharing.rendering.dto.RenderDataResponse
import org.edu_sharing.rendering.dto.RenderModules
import org.edu_sharing.rendering.dto.mapper.Mapper
import org.edu_sharing.rendering.entity.RenderingJob
import org.edu_sharing.rendering.entity.SubJob
import org.edu_sharing.rendering.modules.DefaultStrategy
import org.edu_sharing.rendering.modules.RenderModule
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class HtmlRenderModule(
    @Value("\${app.session.html.nodePermissionExpirationTime}")
    private val nodePermissionExpirationTime: Long,
    private val defaultStrategy: DefaultStrategy,
    private val mapper: Mapper
) : RenderModule {
    override fun module() = RenderModules.HTML

    override fun handle(request: RenderDataRequest): RenderDataResponse {
        val cacheObject = mapper.renderDataRequestToCacheObject(request)
        val links = defaultStrategy.getObjectLinkList(cacheObject)
        return RenderDataResponse(
            objectLinks = links,
            module = module()
        )
    }

    /**
     * This is not needed at the moment. No jobs for HTML files are created
     */
    override fun getObjectLinkFromJobData(subJob: SubJob, renderingJob: RenderingJob): ObjectLink? {
        return null
    }

    override fun getNodePermissionExpirationTime() = nodePermissionExpirationTime
}
