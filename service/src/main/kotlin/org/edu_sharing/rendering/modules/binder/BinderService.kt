package org.edu_sharing.rendering.modules.binder

import org.edu_sharing.generated.repository.backend.services.rest.client.model.Node
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.modules.binder.dto.BinderSubJobMessage
import org.edu_sharing.rendering.modules.binder.git.GitServiceRegistry
import org.edu_sharing.rendering.renderingJob.entity.JobStatus
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.SubJob
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
    @Value("\${app.queue.topicExchange}")
    lateinit var topicExchangeName: String

    @Value("\${app.queue.binder.key}")
    lateinit var binderJobRoutingKey: String

    @Value("\${app.queue.binderPreview.key}")
    lateinit var previewJobRoutingKey: String

    private val log = LoggerFactory.getLogger(javaClass)

    fun createJobs(
        node: Node,
        module: BinderRenderModule
    ): String {
        // get existing job if present!
        val job = mapper.nodeToRenderingJob(node, module.module(), true)

        jobRepository.save(job)
        val binderUploadSubJob = SubJob(
            status = JobStatus.QUEUED,
            parent = job,
            routingKey = binderJobRoutingKey
        )
        subJobRepository.save(binderUploadSubJob)
        amqpTemplate.convertAndSend(topicExchangeName, binderJobRoutingKey, BinderSubJobMessage(
            subJobId = binderUploadSubJob.id.toString()
        ))
        createPreviewSubJob(job,module)

        return job.id.toString()
    }

    private fun createPreviewSubJob(job: RenderingJob, module: BinderRenderModule) {
        val gitService = gitServiceRegistry.getService(job.externalUrl ?: "")
        if (gitService == null) {
            log.info("Preview not available. No GitService found for ${job.externalUrl}")
            return
        }
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
            status = JobStatus.QUEUED,
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
    }

    private fun hasGitHubApiToken(module: BinderRenderModule, job: RenderingJob): Boolean {
        val config = module.getConfig(job.repoId)
        return config.containsKey("githubtoken") && !config["githubtoken"].isNullOrBlank()
    }
}