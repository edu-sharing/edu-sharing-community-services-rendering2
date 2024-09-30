package org.edu_sharing.rendering.core

import org.edu_sharing.rendering.core.annotation.ConditionalOnController
import org.edu_sharing.rendering.core.dto.RenderDataRequest
import org.edu_sharing.rendering.core.dto.RenderDataResponse
import org.edu_sharing.rendering.modules.ModuleRegistry
import org.edu_sharing.rendering.modules.RenderModule
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service

@ConditionalOnController
@Service
class RenderDataService(
    private val moduleRegistry: ModuleRegistry,
) {
    @PreAuthorize("hasPermission(#request.nodeId, 'Read')")
    fun getRenderData(request: RenderDataRequest): RenderDataResponse {
        val renderModule: RenderModule = moduleRegistry.getRenderModule(request.type, request.mimeType)
        return renderModule.handle(request)
    }
}
