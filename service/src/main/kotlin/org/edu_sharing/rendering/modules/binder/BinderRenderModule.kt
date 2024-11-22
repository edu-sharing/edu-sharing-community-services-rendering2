package org.edu_sharing.rendering.modules.binder

import org.edu_sharing.rendering.core.dto.RenderDataRequest
import org.edu_sharing.rendering.core.dto.RenderDataResponse
import org.edu_sharing.rendering.modules.ModuleTypeDefinition
import org.edu_sharing.rendering.modules.ModuleTypeMapper
import org.edu_sharing.rendering.modules.RenderModule
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class BinderRenderModule(
    private val jobService: BinderJobService,
    @Value("\${app.session.moodle.nodePermissionExpirationTime}")
    private val nodePermissionExpirationTime: Long?,
): RenderModule, ModuleTypeMapper {

    override fun module() = "BINDER"

    override fun handle(request: RenderDataRequest): RenderDataResponse {
        val jobId = jobService.createJob(request, module())
        return RenderDataResponse(jobId = jobId)
    }

    override fun getNodePermissionExpirationTime() = nodePermissionExpirationTime
    override fun moduleTypeAssociations() = listOf(ModuleTypeDefinition(type = "url-binder") to this)

}