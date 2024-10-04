package org.edu_sharing.rendering.modules.jupyter

import org.edu_sharing.rendering.core.dto.RenderDataRequest
import org.edu_sharing.rendering.core.dto.RenderDataResponse
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.modules.ConversionModule
import org.edu_sharing.rendering.modules.ModuleTypeDefinition
import org.edu_sharing.rendering.modules.ModuleTypeMapper
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
) : RenderModule, ModuleTypeMapper, ConversionModule {

    @Value("\${app.queue.jupyter.key}")
    lateinit var jupyterKey: String

    @Value("\${app.queue.topicExchange}")
    lateinit var topicExchangeName: String

    override fun module() = "JUPYTER"

    override fun handle(request: RenderDataRequest): RenderDataResponse {
        val cacheObject = mapper.renderDataRequestToCacheObject(request)
        val objectLinks = jupyterJobService.getObjectLinks(cacheObject, this)

        if (objectLinks !== null) {
            return RenderDataResponse(objectLinks = objectLinks, module = module())
        }

        return RenderDataResponse(jobId = jupyterJobService.retrieveOrCreateJob(cacheObject, this), module = module())
    }

    override fun getNodePermissionExpirationTime() = nodePermissionExpirationTime

    override fun moduleTypeAssociations() = listOf(ModuleTypeDefinition(type = "file-jupyter") to this)

    override fun createJob(
        renderingJob: RenderingJob,
        message: RenderingJobMessage
    ) {
        val documentJob = SubJob(routingKey = jupyterKey, parent = renderingJob)
        subJobRepository.save(documentJob)
        renderingJob.subJobs.add(documentJob)
        amqpTemplate.convertAndSend(topicExchangeName, jupyterKey, SubJobMessage(renderingJob.id.toString()))
    }

    fun getTargetMimetype() = MediaType.TEXT_HTML_VALUE
}