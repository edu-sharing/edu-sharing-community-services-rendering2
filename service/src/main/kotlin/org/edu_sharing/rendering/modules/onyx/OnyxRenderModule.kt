package org.edu_sharing.rendering.modules.onyx

import org.edu_sharing.generated.repository.backend.services.rest.client.model.Node
import org.edu_sharing.rendering.core.dto.RenderDataResponse
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.edusharingRepo.services.RepositoryRegistrationStorageService
import org.edu_sharing.rendering.modules.RenderModule
import org.edu_sharing.rendering.modules.ThirdPartyModule
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.edu_sharing.rendering.renderingJob.queue.RenderingJobMessage
import org.edu_sharing.rendering.renderingJob.repository.RenderingJobRepository
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.slf4j.LoggerFactory
import org.springframework.amqp.core.AmqpTemplate
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class OnyxRenderModule(
    private val repositoryRegistrationStorageService: RepositoryRegistrationStorageService,
    private val mapper: Mapper,
    private val jobRepository: RenderingJobRepository,
    private val subJobRepository: SubJobRepository,
    private val amqpTemplate: AmqpTemplate,
    @Value("\${app.queue.topicExchange}")
    private val topicExchangeName: String,
    @Value("\${app.queue.onyx.key}")
    private val jobRoutingKey: String
): RenderModule, ThirdPartyModule {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private val requiredCredentialKeys = setOf("onyxresturl, onyxrunurl, returnservice")
    }

    override fun module() = "ONYX"

    override fun isOptionalModule() = true

    override fun handle(
        node: Node
    ): RenderDataResponse {
        log.debug("Handling node ${node.ref.id} via module ${module()}, creating async job")
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
        log.debug("Sending Onyx job message for jobId ${job.id} to queue $jobRoutingKey")
        amqpTemplate.convertAndSend(topicExchangeName, jobRoutingKey, RenderingJobMessage(id = job.id.toString()))
        return RenderDataResponse(jobId = job.id.toString(), module = module())
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
}
