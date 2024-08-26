package org.edu_sharing.rendering.modules.image

import org.edu_sharing.rendering.dto.ObjectLink
import org.edu_sharing.rendering.dto.RenderDataRequest
import org.edu_sharing.rendering.dto.RenderDataResponse
import org.edu_sharing.rendering.dto.RenderModules
import org.edu_sharing.rendering.dto.mapper.Mapper
import org.edu_sharing.rendering.entity.RenderingJob
import org.edu_sharing.rendering.entity.SubJob
import org.edu_sharing.rendering.modules.DirectStorageHandler
import org.edu_sharing.rendering.modules.RenderModule
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class ImageRenderModule(
    @Value("\${app.session.image.nodePermissionExpirationTime}")
    private val nodePermissionExpirationTime: Long?,
    private val mapper: Mapper,
    private val imageService: ImageService,
    private val directStorageHandler: DirectStorageHandler
) : RenderModule {
    override fun module() = RenderModules.IMAGE

    override fun handle(request: RenderDataRequest): RenderDataResponse {
        val cacheObject = mapper.renderDataRequestToCacheObject(request)
        val isConversionObject = imageService.isConversionObject(cacheObject)
        if (!isConversionObject) {
            return RenderDataResponse(
                objectLinks = directStorageHandler.getObjectLinkList(cacheObject),
                module = module()
            )
        }

        val objectLinks = imageService.getObjectLinks(cacheObject)
        val missingQualities = imageService.getMissingQualities(objectLinks)
        if (missingQualities.isEmpty()) {
            return RenderDataResponse(objectLinks = objectLinks, module = module())
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
