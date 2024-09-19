package org.edu_sharing.rendering.modules.image

import org.edu_sharing.rendering.dto.ObjectLink
import org.edu_sharing.rendering.dto.RenderDataRequest
import org.edu_sharing.rendering.dto.RenderDataResponse
import org.edu_sharing.rendering.modules.RenderModules
import org.edu_sharing.rendering.dto.mapper.Mapper
import org.edu_sharing.rendering.entity.RenderingJob
import org.edu_sharing.rendering.entity.SubJob
import org.edu_sharing.rendering.modules.RenderModule
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class ImageRenderModule(
    @Value("\${app.session.image.nodePermissionExpirationTime}")
    private val nodePermissionExpirationTime: Long?,
    private val mapper: Mapper,
    private val imageService: ImageService,
) : RenderModule {
    override fun module() = RenderModules.IMAGE

    override fun handle(request: RenderDataRequest): RenderDataResponse {
        val cacheObject = mapper.renderDataRequestToCacheObject(request)
        val objectLinks = imageService.getObjectLinks(cacheObject)
        val isConversionType = imageService.isConversionObject(cacheObject)

        // Non-conversion type and already in cache
        if (!isConversionType && objectLinks != null ) {
            return RenderDataResponse(objectLinks = objectLinks, module = module())
        }

        // Missing qualities only apply to  conversion objects
        var missingQualities: List<Int> = emptyList()
        if (isConversionType) {
            missingQualities = imageService.getMissingQualities(objectLinks)
            if (missingQualities.isEmpty()) {
                // None missing, so no further action is needed
                return RenderDataResponse(objectLinks = objectLinks, module = module())
            }
        }

        val jobId = imageService.retrieveOrCreateJob(cacheObject, module(), missingQualities)
        return RenderDataResponse(objectLinks = objectLinks, jobId = jobId, module = module())
    }

    override fun getObjectLinkFromJobData(subJob: SubJob, renderingJob: RenderingJob): ObjectLink? {
        val cacheObject = mapper.renderingJobToCacheObject(renderingJob)
        val links = imageService.getObjectLinks(cacheObject, subJob.quality)
        return links?.get(0)
    }

    override fun getNodePermissionExpirationTime() = nodePermissionExpirationTime
}
