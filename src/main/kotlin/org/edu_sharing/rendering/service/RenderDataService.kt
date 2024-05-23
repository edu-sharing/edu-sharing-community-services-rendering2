package org.edu_sharing.rendering.service

import org.edu_sharing.rendering.config.annotation.ConditionalOnController
import org.edu_sharing.rendering.dto.RenderDataRequest
import org.edu_sharing.rendering.dto.RenderDataResponse
import org.edu_sharing.rendering.modules.ModuleRegistry
import org.edu_sharing.rendering.modules.RenderModule
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service

@ConditionalOnController
@Service
class RenderDataService(
    private val renderModuleMappingService: RenderModuleMappingService,
    private val moduleRegistry: ModuleRegistry,
) {
    @PreAuthorize("hasPermission(#request.nodeId, 'Read')")
    fun getRenderData(request: RenderDataRequest): RenderDataResponse {
        val module = renderModuleMappingService.getModule(request.type, request.mimeType)
        val renderModule: RenderModule = moduleRegistry.getRenderModule(module)
        val response = renderModule.handle(request)
        return response
    }
}
