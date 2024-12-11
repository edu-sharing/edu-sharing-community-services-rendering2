package org.edu_sharing.rendering.modules.eduhtml

import org.edu_sharing.rendering.core.dto.ObjectLink
import org.edu_sharing.rendering.core.dto.RenderDataRequest
import org.edu_sharing.rendering.core.dto.RenderDataResponse
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.core.exception.ResourceNotFoundException
import org.edu_sharing.rendering.modules.ModuleTypeDefinition
import org.edu_sharing.rendering.modules.ModuleTypeMapper
import org.edu_sharing.rendering.modules.RenderModule
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class EduHtmlRenderModule(
    @Value("\${app.session.eduHtml.nodePermissionExpirationTime}")
    private val nodePermissionExpirationTime: Long?,
    private val eduHtmlService: EduHtmlService,
    private val mapper: Mapper
) : RenderModule, ModuleTypeMapper {
    override fun module() = "EDUHTML"
    override fun isOptionalModule() = true


    override fun handle(request: RenderDataRequest): RenderDataResponse {
        val staticLink = try {
            val cacheObject = mapper.renderDataRequestToCacheObject(request)
            eduHtmlService.getObjectLink(cacheObject)
        } catch (_: ResourceNotFoundException) {
            null
        }

        if (staticLink == null) {
            return RenderDataResponse(
                module = module(),
                objectLinks = mutableListOf(),
                jobId = eduHtmlService.createJob(request, module())
            )
        }

        return RenderDataResponse(
            module = module(),
            objectLinks = mutableListOf(staticLink),
            jobId = null
        )
    }

    override fun getObjectLinkFromJobData(subJob: SubJob, renderingJob: RenderingJob): ObjectLink? {
        return ObjectLink(link = subJob.message ?: "")
    }

    override fun getNodePermissionExpirationTime() = nodePermissionExpirationTime

    override fun moduleTypeAssociations() =
        listOf(ModuleTypeDefinition(type = "file-eduhtml") to this)
}
