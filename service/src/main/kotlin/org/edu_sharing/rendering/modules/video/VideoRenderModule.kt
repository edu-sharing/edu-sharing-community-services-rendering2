package org.edu_sharing.rendering.modules.video

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
class VideoRenderModule (
    @Value("\${app.session.video.nodePermissionExpirationTime}")
    private val nodePermissionExpirationTime: Long?,
    private val defaultStrategy: DefaultStrategy,
    private val mapper: Mapper,
    private val videoService: VideoService
): RenderModule {
    override fun module() = RenderModules.VIDEO

    override fun handle(request: RenderDataRequest): RenderDataResponse {
        val cacheObject = mapper.renderDataRequestToCacheObject(request)
        if (!videoService.isConversionObject(cacheObject)) {
            return RenderDataResponse(
                objectLinks = defaultStrategy.getObjectLinkList(cacheObject),
                module = module()
            )
        }

        val objectLinks = videoService.getObjectLinks(cacheObject)
        val missingQualities = videoService.getMissingQualities(objectLinks)
        if (missingQualities.isEmpty()) {
            return RenderDataResponse(objectLinks = objectLinks, module = module())
        }

        val jobId = videoService.retrieveOrCreateJob(cacheObject, module(), missingQualities)
        return RenderDataResponse(objectLinks = objectLinks, jobId = jobId, module = module())
    }

    override fun getObjectLinkFromJobData(subJob: SubJob, renderingJob: RenderingJob): ObjectLink? {
        val cacheObject = mapper.renderingJobToCacheObject(renderingJob)
        val links = videoService.getObjectLinks(cacheObject, subJob.quality)
        return links?.get(0)
    }

    override fun getNodePermissionExpirationTime() = nodePermissionExpirationTime
}
