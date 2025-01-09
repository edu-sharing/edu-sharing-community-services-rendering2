package org.edu_sharing.rendering.core

import org.edu_sharing.rendering.core.annotation.ConditionalOnController
import org.edu_sharing.rendering.core.dto.RenderDataRequest
import org.edu_sharing.rendering.core.dto.RenderDataResponse
import org.edu_sharing.rendering.modules.ModuleRegistry
import org.edu_sharing.rendering.modules.RenderModule
import org.edu_sharing.rendering.security.ModulePermissionService
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service

@ConditionalOnController
@Service
class RenderDataService(
    private val moduleRegistry: ModuleRegistry,
    private val modulePermissionService: ModulePermissionService,
    @Value("\${app.security.enabled}")
    private val securityEnabled: Boolean,
) {
    @PreAuthorize("hasPermission(#request.nodeId, 'Read')")
    fun getRenderData(request: RenderDataRequest): RenderDataResponse {
        val renderModule: RenderModule = moduleRegistry.getRenderModule(request.type, request.mimeType, request.replicationSource ?: "" )

        // todo I don't like the direct access
        if(securityEnabled && !modulePermissionService.hasModuleAccess(renderModule)){
            throw AccessDeniedException("Rendering module ${renderModule.module()} not available")
        }

        return renderModule.handle(request)
    }
}
