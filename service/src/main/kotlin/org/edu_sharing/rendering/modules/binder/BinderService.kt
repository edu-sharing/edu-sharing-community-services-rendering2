package org.edu_sharing.rendering.modules.binder

import org.edu_sharing.generated.repository.backend.services.rest.client.model.Node
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.modules.binder.dto.BinderSubJobMessage
import org.edu_sharing.rendering.modules.binder.exception.MissingGitServiceException
import org.edu_sharing.rendering.modules.binder.git.GitService
import org.edu_sharing.rendering.modules.binder.git.GitServiceRegistry
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.edu_sharing.rendering.renderingJob.entity.SubJobStatus
import org.edu_sharing.rendering.renderingJob.repository.RenderingJobRepository
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.slf4j.LoggerFactory
import org.springframework.amqp.core.AmqpTemplate
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient

@Service
class BinderService(
    private val mapper: Mapper,
    private val jobRepository: RenderingJobRepository,
    private val subJobRepository: SubJobRepository,
    private val amqpTemplate: AmqpTemplate,
    private val gitServiceRegistry: GitServiceRegistry,
    @Autowired(required = false)
    @Qualifier("jupyterConverterWebClient")
    private var jupyterConverterWebClient: WebClient?
) {
    @Value($$"${app.queue.topicExchange}")
    lateinit var topicExchangeName: String

    @Value($$"${app.queue.binder.key}")
    lateinit var binderJobRoutingKey: String

    @Value($$"${app.queue.binder-preview.key}")
    lateinit var previewJobRoutingKey: String

    private val log = LoggerFactory.getLogger(javaClass)

    fun createJobs(
        node: Node,
        module: BinderRenderModule
    ): String {
        log.debug("Creating Binder jobs for nodeId={}", node.ref.id)
        val url = node.properties?.get("ccm:wwwurl")?.firstOrNull() ?: ""
        val gitService = gitServiceRegistry.getService(url)
        if (gitService == null) {
            log.warn("GitService not found for: $url")
            throw MissingGitServiceException("No git service found for url: $url")
        }
        // get existing job if present!
        val job = mapper.nodeToRenderingJob(node, module.module(), true)

        jobRepository.save(job)
        log.debug("Binder main job saved: jobId={} for nodeId={}", job.id, node.ref.id)
        val binderUploadSubJob = SubJob(
            status = SubJobStatus.QUEUED,
            parent = job,
            routingKey = binderJobRoutingKey
        )
        subJobRepository.save(binderUploadSubJob)
        amqpTemplate.convertAndSend(topicExchangeName, binderJobRoutingKey, BinderSubJobMessage(
            subJobId = binderUploadSubJob.id.toString()
        ))
        log.debug("Binder upload sub-job enqueued: subJobId={}, routingKey={}", binderUploadSubJob.id, binderJobRoutingKey)
        createPreviewSubJob(job, module, gitService)

        return job.id.toString()
    }

    private fun createPreviewSubJob(job: RenderingJob, module: BinderRenderModule, gitService: GitService) {
        if (jupyterConverterWebClient == null) {
            log.trace("Preview not available. Jupyter converter web client not found")
            return
        }
        if (!gitService.identifyDeepLink(job.externalUrl ?: "")) {
            log.trace("Preview not available. ${job.externalUrl} is not a deep link")
            return
        }
        // TODO make config more flexible for different VCS providers
        if (!hasGitHubApiToken(module, job)) {
            log.info("Preview not available. No GitHub API token")
            return
        }

        val previewSubJob = SubJob(
            status = SubJobStatus.QUEUED,
            parent = job,
            routingKey = binderJobRoutingKey,
            additionalData = mapOf("preview" to "preview")
        )
        subJobRepository.save(previewSubJob)
        amqpTemplate.convertAndSend(
            topicExchangeName,
            previewJobRoutingKey,
            BinderSubJobMessage(subJobId = previewSubJob.id.toString())
        )
        log.debug("Binder preview sub-job enqueued: subJobId={}, routingKey={}", previewSubJob.id, previewJobRoutingKey)
    }

    private fun hasGitHubApiToken(module: BinderRenderModule, job: RenderingJob): Boolean {
        val config = module.getCredentials(job.repoId)
        return config.containsKey("githubtoken") && !config["githubtoken"].isNullOrBlank()
    }
}
