package org.edu_sharing.rendering.modules.h5p

import org.edu_sharing.generated.repository.backend.services.rest.client.model.Node
import org.edu_sharing.rendering.core.dto.ObjectLink
import org.edu_sharing.rendering.core.dto.RenderDataResponse
import org.edu_sharing.rendering.edusharingRepo.services.RepositoryRegistrationStorageService
import org.edu_sharing.rendering.modules.RenderModule
import org.edu_sharing.rendering.modules.ThirdPartyModule
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.edu_sharing.rendering.utils.storageNodeId
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class H5pRenderModule(
    @Value($$"${app.session.h5p.nodePermissionExpirationTime}")
    private val nodePermissionExpirationTime: Long?,
    private val h5pJobService: H5pJobService,
    private val repositoryRegistrationStorageService: RepositoryRegistrationStorageService,
    @Value($$"${app.security.enabled}")
    private val securityEnabled: Boolean
): RenderModule, ThirdPartyModule {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun module() = "H5P"

    override fun isOptionalModule() = true

    /**
     * Mongo + one AMQP publish, nothing else: this runs on the synchronous renderdata path, so it must never
     * block on lumi. Whether the package is already imported is decided asynchronously by
     * [H5pLookupReceiver], which resolves the job without an import when it is.
     */
    override fun handle(node: Node): RenderDataResponse {
        log.debug("H5P handle called for nodeId={}, hash={}", node.storageNodeId(), node.content?.hash)
        return RenderDataResponse(
            module = module(),
            objectLinks = mutableListOf(),
            jobId = h5pJobService.createJob(node, module())
        )
    }

    override fun getObjectLinkFromJobData(subJob: SubJob, renderingJob: RenderingJob): ObjectLink? {
        return ObjectLink(link = subJob.message ?: "")
    }

    override fun getNodePermissionExpirationTime() = nodePermissionExpirationTime

    override fun getCspHeader(repoId: String): String? {
        if (!securityEnabled) return "frame-ancestors *"
        val registration = repositoryRegistrationStorageService.getRegistrationByRepoId(repoId)
            .orElseThrow { IllegalArgumentException("Unknown repository id: $repoId") }
        return registration.module[module()]?.cspHeader
    }

    override fun validateThirdPartyCredentials(
        credentials: Map<String, String>,
        repoId: String
    ) {
        return
    }

    override fun getCredentials(repoId: String): Map<String, String> {
        val registration = repositoryRegistrationStorageService.getRegistrationByRepoId(repoId)
            .orElseThrow { IllegalArgumentException("Unknown repository id: $repoId") }
        return registration.module[module()]?.credentials ?: mapOf()
    }
}
