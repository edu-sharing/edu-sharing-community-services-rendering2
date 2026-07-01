package org.edu_sharing.rendering.modules.omega

import org.edu_sharing.generated.repository.backend.services.rest.client.model.Node
import org.edu_sharing.rendering.core.dto.ObjectLink
import org.edu_sharing.rendering.core.dto.RenderDataResponse
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.core.exception.ObjectTypeNotSupportedException
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
class OmegaRenderModule(
    private val repositoryRegistrationStorageService: RepositoryRegistrationStorageService,
    private val mapper: Mapper,
    private val jobRepository: RenderingJobRepository,
    private val amqpTemplate: AmqpTemplate,
    private val subJobRepository: SubJobRepository,
    @param:Value($$"${app.queue.topicExchange}")
    private val topicExchangeName: String,
    @param:Value($$"${app.queue.omega.key}")
    private val jobRoutingKey: String
) : RenderModule, ThirdPartyModule {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private val requiredCredentialKeys = setOf("baseurl")
    }

    override fun getNodePermissionExpirationTime() = null

    override fun module() = "OMEGA"
    override fun isOptionalModule() = true
    override fun fallsThroughOnLocalContent() = true

    override fun handle(node: Node): RenderDataResponse {
        log.debug("Handling Omega node ${node.ref.id}, creating async job")
        val replicationSource = node.properties?.getOrDefault("ccm:replicationsource", mutableListOf(""))[0]
        val replicationSourceId = node.properties?.getOrDefault("ccm:replicationsourceid", mutableListOf(""))[0]
        if (replicationSource.isNullOrBlank() || replicationSourceId.isNullOrBlank()) {
            log.error("Missing replication source data in request. Node: " + node.ref.id)
            throw IllegalArgumentException()
        }

        val credentials = getCredentials(node.ref.repo)
        val whitelist = credentials["identifierPrefixWhitelist"]
        if (!whitelist.isNullOrBlank()) {
            val inWhitelist = whitelist.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .any { replicationSourceId.startsWith(it) }
            if (!inWhitelist) {
                log.info("Object $replicationSourceId not in whitelist, will not trigger omega api")
                throw ObjectTypeNotSupportedException("Identifier $replicationSourceId not in Omega whitelist")
            }
        }

        val role = "teacher"
        //val role = if (SecurityContextUtils.currentUser().primaryAffiliation == "teacher") "teacher" else "learner"

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

        val message = OmegaJobMessage(
            id = job.id.toString(),
            nodeId = job.esObjectId,
            identifier = replicationSourceId,
            role = role
        )
        log.debug(
            "Sending Omega job message for jobId {}, identifier {}, role {} to queue {}",
            job.id,
            replicationSourceId,
            role,
            jobRoutingKey
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
            requiredCredentialKeys = requiredCredentialKeys,
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
        return subJob.additionalData ?: emptyMap()
    }
}
