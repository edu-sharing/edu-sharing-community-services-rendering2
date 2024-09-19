package org.edu_sharing.rendering.modules.video

import org.edu_sharing.rendering.core.dto.ObjectLink
import org.edu_sharing.rendering.core.dto.RenderDataRequest
import org.edu_sharing.rendering.core.dto.RenderDataResponse
import org.edu_sharing.rendering.modules.RenderModules
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.edu_sharing.rendering.modules.RenderModule
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class VideoRenderModule (
    @Value("\${app.session.video.nodePermissionExpirationTime}")
    private val nodePermissionExpirationTime: Long?,
    private val mapper: Mapper,
    private val videoService: VideoService
): RenderModule {
    override fun module() = RenderModules.VIDEO

    override fun handle(request: RenderDataRequest): RenderDataResponse {
        val cacheObject = mapper.renderDataRequestToCacheObject(request)
        val objectLinks = videoService.getObjectLinks(cacheObject)

        val isConversionType = videoService.isConversionObject(cacheObject)

        // Non-conversion type and already in cache
        if (!isConversionType && objectLinks != null ) {
            return RenderDataResponse(objectLinks = objectLinks, module = module())
        }

        // Missing qualities only apply to  conversion objects
        var missingQualities: List<Int> = emptyList()
        if (isConversionType) {
            missingQualities = videoService.getMissingQualities(objectLinks)
            if (missingQualities.isEmpty()) {
                // None missing, so no further action is needed
                return RenderDataResponse(objectLinks = objectLinks, module = module())
            }
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
