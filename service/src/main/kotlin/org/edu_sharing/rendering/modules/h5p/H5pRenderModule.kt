package org.edu_sharing.rendering.modules.h5p

import org.edu_sharing.rendering.config.H5P_BASE_PATH
import org.edu_sharing.rendering.dto.ObjectLink
import org.edu_sharing.rendering.dto.RenderDataRequest
import org.edu_sharing.rendering.dto.RenderDataResponse
import org.edu_sharing.rendering.modules.RenderModules
import org.edu_sharing.rendering.renderingJobs.entity.RenderingJob
import org.edu_sharing.rendering.renderingJobs.entity.SubJob
import org.edu_sharing.rendering.modules.RenderModule
import org.edu_sharing.rendering.modules.h5p.lumi.LumiNodeInfoService
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class H5pRenderModule(
    @Value("\${app.session.h5p.nodePermissionExpirationTime}")
    private val nodePermissionExpirationTime: Long?,
    private val h5pJobService: H5pJobService,
    private val lumiNodeInfoService: LumiNodeInfoService
): RenderModule {
    @Value("\${app.public.url}:\${app.public.port}")
    lateinit var baseUrl: String

    override fun module() = RenderModules.H5P

    override fun handle(request: RenderDataRequest): RenderDataResponse {
        val cachedLumiContentId = lumiNodeInfoService.getContentId(request.nodeId, request.hash)
        if (cachedLumiContentId != null) {
            return RenderDataResponse(
                module = module(),
                objectLinks = mutableListOf(ObjectLink(link = "$baseUrl$H5P_BASE_PATH/${cachedLumiContentId}")),
                jobId = null
            )
        }
        
        return RenderDataResponse(
            module = module(),
            objectLinks = mutableListOf(),
            jobId = h5pJobService.createJob(request, module())
        )
    }

    override fun getObjectLinkFromJobData(subJob: SubJob, renderingJob: RenderingJob): ObjectLink? {
        return ObjectLink(link = subJob.message ?: "")
    }

    override fun getNodePermissionExpirationTime() = nodePermissionExpirationTime
}
