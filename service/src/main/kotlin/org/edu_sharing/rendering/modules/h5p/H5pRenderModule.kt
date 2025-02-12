package org.edu_sharing.rendering.modules.h5p

import org.edu_sharing.rendering.config.AppInfo
import org.edu_sharing.rendering.config.H5P_BASE_PATH
import org.edu_sharing.rendering.core.dto.ObjectLink
import org.edu_sharing.rendering.core.dto.RenderDataRequest
import org.edu_sharing.rendering.core.dto.RenderDataResponse
import org.edu_sharing.rendering.modules.RenderModule
import org.edu_sharing.rendering.modules.h5p.lumi.LumiContentManagementService
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.edu_sharing.rendering.utils.combinePath
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class H5pRenderModule(
    @Value("\${app.session.h5p.nodePermissionExpirationTime}")
    private val nodePermissionExpirationTime: Long?,
    private val h5pJobService: H5pJobService,
    private val lumiContentManagementService: LumiContentManagementService,
    private val appInfo: AppInfo
): RenderModule {

    override fun module() = "H5P"

    override fun isOptionalModule() = true

    override fun handle(request: RenderDataRequest): RenderDataResponse {
        val cachedLumiContentId = lumiContentManagementService.getContentId(request.nodeId, request.hash)
        if (cachedLumiContentId != null) {
            return RenderDataResponse(
                module = module(),
                objectLinks = mutableListOf(ObjectLink(link = appInfo.public.url.combinePath(H5P_BASE_PATH, cachedLumiContentId))),
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
