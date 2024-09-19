package org.edu_sharing.rendering.modules.noConversion

import org.edu_sharing.rendering.core.dto.RenderDataRequest
import org.edu_sharing.rendering.core.dto.RenderDataResponse
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.core.exception.ResourceNotFoundException
import org.edu_sharing.rendering.modules.RenderModule
import org.edu_sharing.rendering.renderingJob.MainJobCreationService
import org.edu_sharing.rendering.storage.StorageService

abstract class BaseNoConversionModule(
    private val nodePermissionExpirationTime: Long?,
    private val mapper: Mapper,
    private val storageService: StorageService,
    private val mainJobCreationService: MainJobCreationService
) : RenderModule {
    override fun handle(request: RenderDataRequest): RenderDataResponse {
        val cacheObject = mapper.renderDataRequestToCacheObject(request)
        val link = try {
            storageService.getObjectLink(cacheObject)
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
