package org.edu_sharing.rendering.modules.av.audio

import org.edu_sharing.generated.repository.backend.services.rest.client.model.Node
import org.edu_sharing.rendering.core.dto.ObjectLink
import org.edu_sharing.rendering.core.dto.RenderDataResponse
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.modules.ConversionModule
import org.edu_sharing.rendering.modules.RenderModule
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.edu_sharing.rendering.renderingJob.queue.PriorityPostProcessor
import org.edu_sharing.rendering.renderingJob.queue.RenderingJobMessage
import org.edu_sharing.rendering.renderingJob.queue.SubJobMessage
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.slf4j.LoggerFactory
import org.springframework.amqp.core.AmqpTemplate
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class AudioRenderModule(
    @Value($$"${app.session.audio.nodePermissionExpirationTime}")
    private val nodePermissionExpirationTime: Long?,
    private val mapper: Mapper,
    private val audioService: AudioService,
    private val subJobRepository: SubJobRepository,
    private val amqpTemplate: AmqpTemplate
    ) : RenderModule, ConversionModule {

    private val log = LoggerFactory.getLogger(javaClass)

    @Value($$"${app.queue.av.key}")
    lateinit var avRoutingKey: String

    @Value($$"${app.queue.topicExchange}")
    lateinit var topicExchangeName: String

    override fun module() = "AUDIO"

    override fun handle(node: Node): RenderDataResponse {
        val cacheObject = mapper.nodeToCacheObject(node)
        log.debug("handle: nodeId=${cacheObject.nodeId}, mimeType=${cacheObject.mimeType}")
        val objectLinks = audioService.getObjectLinks(cacheObject)

        if (objectLinks !== null) {
            log.debug("Cache hit for audio nodeId=${cacheObject.nodeId}, returning direct link")
            return RenderDataResponse(objectLinks = objectLinks, module = module())
        }
        val jobId = audioService.retrieveOrCreateJob(cacheObject, module())
        log.debug("Audio conversion job dispatched: jobId=$jobId for nodeId=${cacheObject.nodeId}")
        return RenderDataResponse(jobId = jobId, module = module())
    }

    override fun getObjectLinkFromJobData(subJob: SubJob, renderingJob: RenderingJob): ObjectLink? {
        val cacheObject = mapper.renderingJobToCacheObject(renderingJob)
        val links = audioService.getObjectLinks(cacheObject)
        return links?.get(0)
    }

    override fun getNodePermissionExpirationTime() = nodePermissionExpirationTime

    override fun createConversionSubJobs(
        renderingJob: RenderingJob,
        message: RenderingJobMessage
    ) {
        log.debug("Creating ${message.missingQualities.size} audio sub-jobs for jobId=${renderingJob.id}, qualities=${message.missingQualities}")
        val priority = 255
        message.missingQualities.forEach {
            val avJob = SubJob(routingKey = avRoutingKey, quality = it, parent = renderingJob, priority = priority)
            renderingJob.subJobs.add(avJob)
            subJobRepository.save(avJob)
            amqpTemplate.convertAndSend(
                topicExchangeName,
                avRoutingKey,
                SubJobMessage(renderingJob.id.toString(), it),
                PriorityPostProcessor(priority)
            )
        }
    }
}
