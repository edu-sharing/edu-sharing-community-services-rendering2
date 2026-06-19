package org.edu_sharing.rendering.modules.ddb

import org.edu_sharing.generated.repository.backend.services.rest.client.model.Node
import org.edu_sharing.rendering.core.dto.RenderDataResponse
import org.edu_sharing.rendering.edusharingRepo.services.RepositoryRegistrationStorageService
import org.edu_sharing.rendering.modules.RenderModule
import org.edu_sharing.rendering.modules.ThirdPartyModule
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class DdbRenderModule(
    private val repositoryRegistrationStorageService: RepositoryRegistrationStorageService,
    private val jobService: DdbJobService
) : RenderModule, ThirdPartyModule {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private val requiredCredentialKeys = setOf("apiToken")
        const val REST_API_BASE_URL = "https://api.deutsche-digitale-bibliothek.de"
        const val IIIF_API_BASE_URL = "https://iiif.deutsche-digitale-bibliothek.de/image/2"
        const val WIDTH_PLACEHOLDER = "{WIDTH}"
        const val HEIGHT_PLACEHOLDER = "{HEIGHT}"
    }

    override fun isOptionalModule() = true

    override fun module() = "DDB"

    override fun handle(node: Node): RenderDataResponse {
        log.debug("Handling DDB node ${node.ref.id}, remoteId ${node.remote?.id}, creating async job")
        val jobId = jobService.createJob(node, module())
        log.debug("DDB job created with jobId $jobId for node ${node.ref.id}")
        return RenderDataResponse(jobId = jobId, module = module())
    }

    override fun validateThirdPartyCredentials(
        credentials: Map<String, String>, repoId: String
    ) {
        validateCredentials(
            credentials = credentials, requiredCredentialKeys = requiredCredentialKeys, moduleName = module()
        )
    }

    override fun getCredentials(repoId: String): Map<String, String> {
        val registration = repositoryRegistrationStorageService.getRegistrationByRepoId(repoId)
            .orElseThrow { IllegalArgumentException("Unknown repository id: $repoId") }
        return registration.module[module()]?.credentials ?: mapOf()
    }

    override fun getAdditionalData(subJob: SubJob, repoId: String): Map<String, String>? {
        return subJob.additionalData
    }
}
