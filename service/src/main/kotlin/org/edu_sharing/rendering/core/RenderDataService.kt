package org.edu_sharing.rendering.core

import org.edu_sharing.generated.repository.backend.services.rest.client.model.Node
import org.edu_sharing.rendering.core.annotation.ConditionalOnController
import org.edu_sharing.rendering.core.dto.RenderDataRequest
import org.edu_sharing.rendering.core.dto.RenderDataResponse
import org.edu_sharing.rendering.modules.ModuleRegistry
import org.edu_sharing.rendering.modules.RenderModule
import org.edu_sharing.rendering.security.ModulePermissionService
import org.slf4j.LoggerFactory
import org.springframework.security.access.prepost.PostAuthorize
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service

@Service
@ConditionalOnController
class RenderDataService(
    private val moduleRegistry: ModuleRegistry,
    private val modulePermissionService: ModulePermissionService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PreAuthorize("hasPermission(#request.nodeId, 'ReadAll')")
    @PostAuthorize("@modulePermissionService.hasModuleAccess(returnObject, #request.nodeId)")
    fun getRenderModule(request: RenderDataRequest, node: Node) : RenderModule {
        log.debug("Resolving render module: nodeId=${request.nodeId}, repoId=${request.repoId}, mediatype=${node.mediatype}")
        val module: RenderModule = moduleRegistry.getRenderModule(node) {
            modulePermissionService.hasModuleAccess(it, request.nodeId)
        }
        log.debug("Resolved render module: ${module::class.simpleName}, nodeId=${request.nodeId}")
        return module
    }

    /**
     * On-demand render data for a node whose module deferred its (expiring) link fetch (see
     * [RenderModule.rendersDeferred]). The node comes from the trusted session cache, so no signature
     * is re-verified — only the node-permission session gate. Produces a fresh fetch every call.
     */
    @PreAuthorize("hasPermission(#nodeId, 'ReadAll')")
    fun fetchOnDemand(nodeId: String, node: Node): RenderDataResponse {
        val module: RenderModule = moduleRegistry.getRenderModule(node) {
            modulePermissionService.hasModuleAccess(it, nodeId)
        }
        log.debug("On-demand fetch via module ${module::class.simpleName}, nodeId=$nodeId")
        return module.fetchOnDemand(node)
    }
}
