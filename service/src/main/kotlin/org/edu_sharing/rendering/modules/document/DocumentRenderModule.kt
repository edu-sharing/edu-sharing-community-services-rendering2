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
import org.slf4j.LoggerFactory
import org.springframework.amqp.core.AmqpTemplate
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component

@Component
class DocumentRenderModule(
    @Value($$"${app.session.document.nodePermissionExpirationTime}")
    private val nodePermissionExpirationTime: Long?,
    private val mapper: Mapper,
    private val documentService: DocumentService,
    private val subJobRepository: SubJobRepository,
    private val amqpTemplate: AmqpTemplate
) : RenderModule, ConversionModule {

    private val log = LoggerFactory.getLogger(javaClass)

    @Value($$"${app.queue.document.key}")
    lateinit var documentRoutingKey: String

    @Value($$"${app.queue.topicExchange}")
    lateinit var topicExchangeName: String

    override fun module() = "DOCUMENT"
    fun getTargetMimetype() = MediaType.APPLICATION_PDF_VALUE

    override fun handle(node: Node): RenderDataResponse {
        val cacheObject = mapper.nodeToCacheObject(node)
        log.debug("handle: nodeId=${cacheObject.nodeId}, mimeType=${cacheObject.mimeType}, module=${module()}")
        val objectLinks = documentService.getObjectLinks(cacheObject, this)

        if (objectLinks !== null) {
            log.debug("Cache hit for document nodeId=${cacheObject.nodeId}, returning direct link")
            return RenderDataResponse(objectLinks = objectLinks, module = module())
        }

        val jobId = documentService.retrieveOrCreateJob(cacheObject, this)
        log.debug("Document conversion job dispatched: jobId=$jobId for nodeId=${cacheObject.nodeId}")
        return RenderDataResponse(jobId = jobId, module = module())
    }

    override fun getObjectLinkFromJobData(subJob: SubJob, renderingJob: RenderingJob): ObjectLink? {
        val cacheObject = mapper.renderingJobToCacheObject(renderingJob)
        val links = documentService.getObjectLinks(cacheObject, this)
        return links?.get(0)
    }

    override fun getNodePermissionExpirationTime() = nodePermissionExpirationTime

    override fun createConversionSubJobs(
        renderingJob: RenderingJob,
        message: RenderingJobMessage
    ) {
        log.debug("Creating document sub-job for jobId=${renderingJob.id}, nodeId=${renderingJob.esObjectId}")
        val documentJob = SubJob(routingKey = documentRoutingKey, parent = renderingJob)
        subJobRepository.save(documentJob)
        renderingJob.subJobs.add(documentJob)
        amqpTemplate.convertAndSend(topicExchangeName, documentRoutingKey, SubJobMessage(renderingJob.id.toString()))
    }
}
