package org.edu_sharing.rendering.core

import org.edu_sharing.rendering.core.annotation.ConditionalOnController
import org.edu_sharing.rendering.core.dto.RenderDataRequest
import org.edu_sharing.rendering.modules.ModuleRegistry
import org.edu_sharing.rendering.modules.RenderModule
import org.springframework.security.access.prepost.PostAuthorize
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service

@Service
@ConditionalOnController
class RenderDataService(
    private val moduleRegistry: ModuleRegistry,
) {
    @PreAuthorize("hasPermission(#request.nodeId, 'Read')")
    @PostAuthorize("@modulePermissionService.hasModuleAccess(returnObject, #request.nodeId)")
    fun getRenderModule(request: RenderDataRequest) : RenderModule {
        return moduleRegistry.getRenderModule(
            type = request.type,
            mimeType = request.mimeType ?: "",
            replicationSource = request.replicationSource ?: "",
            resourceType = request.resourceType ?: ""
        )
    }
}
