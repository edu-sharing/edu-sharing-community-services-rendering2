package org.edu_sharing.rendering.modules.audio

import org.edu_sharing.rendering.dto.ObjectLink
import org.edu_sharing.rendering.dto.RenderDataRequest
import org.edu_sharing.rendering.dto.RenderDataResponse
import org.edu_sharing.rendering.dto.RenderModules
import org.edu_sharing.rendering.dto.mapper.Mapper
import org.edu_sharing.rendering.entity.RenderingJob
import org.edu_sharing.rendering.entity.SubJob
import org.edu_sharing.rendering.modules.RenderModule
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class AudioRenderModule(
    @Value("\${app.session.audio.nodePermissionExpirationTime}")
    private val nodePermissionExpirationTime: Long,
    private val mapper: Mapper,
    private val audioService: AudioService
) : RenderModule {
    override fun module() = RenderModules.AUDIO

    override fun handle(request: RenderDataRequest): RenderDataResponse {
        val cacheObject = mapper.renderDataRequestToCacheObject(request)
        val objectLinks = audioService.getObjectLinks(cacheObject)
        if (objectLinks !== null) return RenderDataResponse(objectLinks = objectLinks, module = module())
        return RenderDataResponse(jobId = audioService.retrieveOrCreateJob(cacheObject, module()), module = module())
    }

    override fun getObjectLinkFromJobData(subJob: SubJob, renderingJob: RenderingJob): ObjectLink? {
        val cacheObject = mapper.renderingJobToCacheObject(renderingJob)
        val links = audioService.getObjectLinks(cacheObject)
        return links?.get(0)
    }

    override fun getNodePermissionExpirationTime(): Long? {
        return nodePermissionExpirationTime
    }
}
