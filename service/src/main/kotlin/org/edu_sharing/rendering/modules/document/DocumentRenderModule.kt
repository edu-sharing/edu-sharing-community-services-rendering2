package org.edu_sharing.rendering.modules.document

import org.edu_sharing.generated.repository.backend.services.rest.client.model.Node
import org.edu_sharing.rendering.core.dto.ObjectLink
import org.edu_sharing.rendering.core.dto.RenderDataResponse
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
class DocumentRenderModule(
    @Value("\${app.session.document.nodePermissionExpirationTime}")
    private val nodePermissionExpirationTime: Long?,
    private val mapper: Mapper,
    private val documentService: DocumentService,
    private val subJobRepository: SubJobRepository,
    private val amqpTemplate: AmqpTemplate
) : RenderModule, ConversionModule {

    @Value("\${app.queue.document.key}")
    lateinit var documentRoutingKey: String

    @Value("\${app.queue.topicExchange}")
    lateinit var topicExchangeName: String

    override fun module() = "DOCUMENT"
    fun getTargetMimetype() = MediaType.APPLICATION_PDF_VALUE

    override fun handle(node: Node): RenderDataResponse {
        val cacheObject = mapper.nodeToCacheObject(node)
        val objectLinks = documentService.getObjectLinks(cacheObject, this)

        if (objectLinks !== null) {
            return RenderDataResponse(objectLinks = objectLinks, module = module())
        }

        return RenderDataResponse(jobId = documentService.retrieveOrCreateJob(cacheObject, this), module = module())
    }

    override fun getObjectLinkFromJobData(subJob: SubJob, renderingJob: RenderingJob): ObjectLink? {
        val cacheObject = mapper.renderingJobToCacheObject(renderingJob)
        val links = documentService.getObjectLinks(cacheObject, this)
        return links?.get(0)
    }

    override fun getNodePermissionExpirationTime() = nodePermissionExpirationTime

    override fun createJob(
        renderingJob: RenderingJob,
        message: RenderingJobMessage
    ) {
        val documentJob = SubJob(routingKey = documentRoutingKey, parent = renderingJob)
        subJobRepository.save(documentJob)
        renderingJob.subJobs.add(documentJob)
        amqpTemplate.convertAndSend(topicExchangeName, documentRoutingKey, SubJobMessage(renderingJob.id.toString()))
    }
}
