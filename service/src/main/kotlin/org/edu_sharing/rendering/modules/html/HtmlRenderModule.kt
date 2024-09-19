package org.edu_sharing.rendering.modules.html

import org.edu_sharing.rendering.blobStorage.StorageService
import org.edu_sharing.rendering.dto.RenderDataRequest
import org.edu_sharing.rendering.dto.RenderDataResponse
import org.edu_sharing.rendering.modules.RenderModules
import org.edu_sharing.rendering.dto.mapper.Mapper
import org.edu_sharing.rendering.exception.ResourceNotFoundException
import org.edu_sharing.rendering.renderingJobs.MainJobCreationService
import org.edu_sharing.rendering.modules.RenderModule
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class HtmlRenderModule(
    @Value("\${app.session.html.nodePermissionExpirationTime}")
    private val nodePermissionExpirationTime: Long?,
    private val mapper: Mapper,
    private val mainJobCreationService: MainJobCreationService,
    private val storageImplementation: StorageService
) : RenderModule {

    override fun module() = RenderModules.HTML

    override fun handle(request: RenderDataRequest): RenderDataResponse {
        val cacheObject = mapper.renderDataRequestToCacheObject(request)
        val link = try {
            storageImplementation.getObjectLink(cacheObject)
        } catch (_: ResourceNotFoundException) {
            null
        }
        var jobId: String? = null
        if (link == null) {
            jobId = mainJobCreationService.getExistingJobId(cacheObject)
                ?: mainJobCreationService.createMainJob(cacheObject, module())
        }

        return RenderDataResponse(
            objectLinks = if (link == null) null else listOf(link),
            module = module(),
            jobId = jobId,
        )
    }

    override fun getNodePermissionExpirationTime() = nodePermissionExpirationTime
}
