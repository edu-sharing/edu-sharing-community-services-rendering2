package org.edu_sharing.rendering.modules.sodix

import org.edu_sharing.generated.repository.backend.services.rest.client.model.Node
import org.edu_sharing.rendering.core.dto.ObjectLink
import org.edu_sharing.rendering.core.dto.RenderDataResponse
import org.edu_sharing.rendering.core.dto.RequestUserData
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.edusharingRepo.services.RepositoryRegistrationStorageService
import org.edu_sharing.rendering.modules.RenderModule
import org.edu_sharing.rendering.modules.ThirdPartyModule
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.edu_sharing.rendering.renderingJob.repository.RenderingJobRepository
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.slf4j.LoggerFactory
import org.springframework.amqp.core.AmqpTemplate
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class SodixRenderModule(
    private val repositoryRegistrationStorageService: RepositoryRegistrationStorageService,
    private val mapper: Mapper,
    private val jobRepository: RenderingJobRepository,
    private val amqpTemplate: AmqpTemplate,
    private val subJobRepository: SubJobRepository,
    @param:Value($$"${app.queue.topicExchange}")
    private val topicExchangeName: String,
    @param:Value($$"${app.queue.sodix.key}")
    private val jobRoutingKey: String
) : RenderModule, ThirdPartyModule {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private val requiredCredentialKeys = setOf("baseurl")
        private val optionalCredentialKeys = setOf("playoutMimetypes", "allowExternalFrameSrc")
    }

    override fun getNodePermissionExpirationTime() = null

    override fun module() = "SODIX"
    override fun isOptionalModule() = true

    override fun handle(node: Node, userData: RequestUserData): RenderDataResponse {
        val replicationSource = node.properties?.getOrDefault("ccm:replicationsource", mutableListOf(""))[0]
        val replicationSourceId = node.properties?.getOrDefault("ccm:replicationsourceid", mutableListOf(""))[0]
        if (replicationSource.isNullOrBlank() || replicationSourceId.isNullOrBlank()) {
            log.error("Missing replication source data in request. Node: " + node.ref.id)
            throw IllegalArgumentException()
        }
        val job = mapper.nodeToRenderingJob(
            node = node,
            module = module(),
            isConversionType = true
        )
        jobRepository.save(job)

        subJobRepository.save(SubJob(
            routingKey = jobRoutingKey,
            parent = job
        ))

        val isPaidMedia = node.properties?.getOrDefault("ccm:editorial_state", mutableListOf(""))[0] == "restricted_mz"

        val message = SodixJobMessage(
            id = job.id.toString(),
            nodeId = job.esObjectId,
            identifier = replicationSourceId,
            isPaidMedia = isPaidMedia
        )
        amqpTemplate.convertAndSend(topicExchangeName, jobRoutingKey, message)

        return RenderDataResponse(
            jobId = job.id.toString(),
            module = module()
        )
    }

    override fun validateThirdPartyCredentials(
        credentials: Map<String, String>,
        repoId: String
    ) {
        validateCredentials(
            credentials = credentials,
            requiredCredentialKeys = requiredCredentialKeys.filterNot { optionalCredentialKeys.contains(it) }.toSet(),
            moduleName = module()
        )
    }

    override fun getCredentials(repoId: String): Map<String, String> {
        val registration = repositoryRegistrationStorageService.getRegistrationByRepoId(repoId)
            .orElseThrow { IllegalArgumentException("Unknown repository id: $repoId") }
        return registration.module[module()]?.credentials ?: mapOf()
    }

    override fun getObjectLinkFromJobData(subJob: SubJob, renderingJob: RenderingJob): ObjectLink? {
        return subJob.message?.let { ObjectLink(link = it) }
    }

    override fun getAdditionalData(subJob: SubJob, repoId: String): Map<String, String>? {
        val clientSettings = getCredentials(repoId).filter { optionalCredentialKeys.contains(it.key) }
        val subJobAdditionalData = subJob.additionalData ?: emptyMap()

        return clientSettings + subJobAdditionalData
    }
}
