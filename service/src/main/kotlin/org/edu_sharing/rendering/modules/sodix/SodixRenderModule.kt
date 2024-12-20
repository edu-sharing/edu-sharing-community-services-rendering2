package org.edu_sharing.rendering.modules.sodix

import org.edu_sharing.rendering.core.dto.RenderDataRequest
import org.edu_sharing.rendering.core.dto.RenderDataResponse
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.edusharingRepo.services.RepositoryRegistrationStorageService
import org.edu_sharing.rendering.modules.RenderModule
import org.edu_sharing.rendering.modules.ThirdPartyModule
import org.edu_sharing.rendering.renderingJob.entity.JobStatus
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
) : RenderModule, ThirdPartyModule {

    private val log = LoggerFactory.getLogger(javaClass)

    @Value("\${app.queue.topicExchange}")
    lateinit var topicExchangeName: String

    @Value("\${app.queue.sodix.key}")
    lateinit var jobRoutingKey: String

    companion object {
        private val requiredCredentialKeys = setOf("baseurl")
    }

    override fun getNodePermissionExpirationTime() = null

    override fun module() = "SODIX"
    override fun isOptionalModule() = true

    override fun handle(request: RenderDataRequest): RenderDataResponse {
        if (request.replicationSource.isNullOrBlank() || request.replicationSourceId.isNullOrBlank()) {
            log.error("Missing replication source data in request. Node: " + request.nodeId)
            throw IllegalArgumentException()
        }
        val job = mapper.renderDataRequestToRenderingJob(request, module())
        jobRepository.save(job)

        subJobRepository.save(SubJob(
            status = JobStatus.QUEUED,
            routingKey = jobRoutingKey,
            parent = job
        ))

        if (request.replicationSourceFlag) {
            subJobRepository.save(SubJob(
                status = JobStatus.QUEUED,
                routingKey = jobRoutingKey,
                parent = job,
                quality = 1
            ))
        }

        val message = SodixJobMessage(
            id = job.id.toString(),
            nodeId = job.esObjectId,
            identifier = request.replicationSourceId,
        )
        amqpTemplate.convertAndSend(topicExchangeName, jobRoutingKey, message)

        return RenderDataResponse(jobId = job.id.toString())
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

    override fun getConfig(repoId: String): Map<String, String> {
        val registration = repositoryRegistrationStorageService.getRegistrationByRepoId(repoId)
            .orElseThrow { IllegalArgumentException("Unknown repository id: $repoId") }
        return registration.module[module()]?.credentials ?: mapOf()
    }
}