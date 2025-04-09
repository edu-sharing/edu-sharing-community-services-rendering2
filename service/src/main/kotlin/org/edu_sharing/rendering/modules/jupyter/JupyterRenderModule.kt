package org.edu_sharing.rendering.modules.jupyter

import org.edu_sharing.generated.repository.backend.services.rest.client.model.Node
import org.edu_sharing.rendering.core.dto.ObjectLink
import org.edu_sharing.rendering.core.dto.RenderDataResponse
import org.edu_sharing.rendering.core.dto.RequestUserData
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.modules.ConversionModule
import org.edu_sharing.rendering.modules.RenderModule
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.edu_sharing.rendering.renderingJob.queue.RenderingJobMessage
import org.edu_sharing.rendering.renderingJob.queue.SubJobMessage
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.springframework.amqp.core.AmqpTemplate
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component

@Component
class JupyterRenderModule(
    @Value("\${app.session.moodle.nodePermissionExpirationTime}")
    private val nodePermissionExpirationTime: Long?,
    private val mapper: Mapper,
    private val jupyterJobService: JupyterJobService,
    private val subJobRepository: SubJobRepository,
    private val amqpTemplate: AmqpTemplate
) : RenderModule, ConversionModule {

    @Value("\${app.queue.jupyter.key}")
    lateinit var jupyterKey: String

    @Value("\${app.queue.topicExchange}")
    lateinit var topicExchangeName: String

    override fun module() = "JUPYTER"

    override fun isOptionalModule() = true

    override fun handle(node: Node, requestUserData: RequestUserData): RenderDataResponse {
        val cacheObject = mapper.nodeToCacheObject(node)
        val objectLinks = jupyterJobService.getObjectLinks(cacheObject, this)

        if (objectLinks !== null) {
            return RenderDataResponse(objectLinks = objectLinks, module = module())
        }

        return RenderDataResponse(jobId = jupyterJobService.retrieveOrCreateJob(cacheObject, this), module = module())
    }

    override fun getObjectLinkFromJobData(subJob: SubJob, renderingJob: RenderingJob): ObjectLink? {
        val cacheObject = mapper.renderingJobToCacheObject(renderingJob)
        val links = jupyterJobService.getObjectLinks(cacheObject, this)
        return links?.get(0)
    }

    override fun getAdditionalDataFromSubJob(subJob: SubJob): Map<String, String>? {
        return subJob.additionalData
    }

    override fun getNodePermissionExpirationTime() = nodePermissionExpirationTime

    override fun createConversionSubJobs(
        renderingJob: RenderingJob,
        message: RenderingJobMessage
    ) {
        val jupyterJob = SubJob(routingKey = jupyterKey, parent = renderingJob)
        subJobRepository.save(jupyterJob)
        renderingJob.subJobs.add(jupyterJob)
        amqpTemplate.convertAndSend(topicExchangeName, jupyterKey, SubJobMessage(renderingJob.id.toString()))
    }

    fun getTargetMimetype() = MediaType.TEXT_HTML_VALUE
}
