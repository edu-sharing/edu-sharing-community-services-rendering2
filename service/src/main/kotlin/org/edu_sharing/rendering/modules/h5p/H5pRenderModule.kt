package org.edu_sharing.rendering.modules.h5p

import org.edu_sharing.generated.repository.backend.services.rest.client.model.Node
import org.edu_sharing.rendering.config.AppInfo
import org.edu_sharing.rendering.config.H5P_BASE_PATH
import org.edu_sharing.rendering.core.dto.ObjectLink
import org.edu_sharing.rendering.core.dto.RenderDataResponse
import org.edu_sharing.rendering.edusharingRepo.services.RepositoryRegistrationStorageService
import org.edu_sharing.rendering.modules.RenderModule
import org.edu_sharing.rendering.modules.ThirdPartyModule
import org.edu_sharing.rendering.modules.h5p.lumi.LumiContentManagementService
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.edu_sharing.rendering.utils.combinePath
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class H5pRenderModule(
    @Value("\${app.session.h5p.nodePermissionExpirationTime}")
    private val nodePermissionExpirationTime: Long?,
    private val h5pJobService: H5pJobService,
    private val lumiContentManagementService: LumiContentManagementService,
    private val appInfo: AppInfo,
    private val repositoryRegistrationStorageService: RepositoryRegistrationStorageService,
    @Value("\${app.security.enabled}")
    private val securityEnabled: Boolean
): RenderModule, ThirdPartyModule {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun module() = "H5P"

    override fun isOptionalModule() = true

    override fun handle(node: Node): RenderDataResponse {
        log.debug("H5P handle called for nodeId={}, hash={}", node.ref.id, node.content?.hash)
        val cachedLumiContentId = lumiContentManagementService.getContentId(node.ref.id, node.content?.hash ?: "")
        if (cachedLumiContentId != null) {
            log.debug("H5P cache hit for nodeId={}, lumiContentId={}", node.ref.id, cachedLumiContentId)
            return RenderDataResponse(
                module = module(),
                objectLinks = mutableListOf(
                    ObjectLink(
                        link = appInfo.public.url.combinePath(H5P_BASE_PATH, cachedLumiContentId)
                    )
                ),
                jobId = null
            )
        }
        
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
