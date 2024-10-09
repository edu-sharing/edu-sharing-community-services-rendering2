package org.edu_sharing.rendering.modules.av.video

import org.edu_sharing.rendering.core.dto.ObjectLink
import org.edu_sharing.rendering.core.dto.RenderDataRequest
import org.edu_sharing.rendering.core.dto.RenderDataResponse
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.modules.ConversionModule
import org.edu_sharing.rendering.modules.ModuleTypeDefinition
import org.edu_sharing.rendering.modules.ModuleTypeMapper
import org.edu_sharing.rendering.modules.RenderModule
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.edu_sharing.rendering.renderingJob.queue.PriorityPostProcessor
import org.edu_sharing.rendering.renderingJob.queue.RenderingJobMessage
import org.edu_sharing.rendering.renderingJob.queue.SubJobMessage
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.springframework.amqp.core.AmqpTemplate
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class VideoRenderModule (
    @Value("\${app.session.video.nodePermissionExpirationTime}")
    private val nodePermissionExpirationTime: Long?,
    private val mapper: Mapper,
    private val videoService: VideoService,
    private val subJobRepository: SubJobRepository,
    private val amqpTemplate: AmqpTemplate,
    private val configuredResolutions: VideoConverterConfig
): RenderModule, ModuleTypeMapper, ConversionModule {

    @Value("\${app.queue.av.key}")
    lateinit var avRoutingKey: String

    @Value("\${app.queue.topicExchange}")
    lateinit var topicExchangeName: String


    override fun module() = "VIDEO"

    override fun handle(request: RenderDataRequest): RenderDataResponse {
        val cacheObject = mapper.renderDataRequestToCacheObject(request)
        val objectLinks = videoService.getObjectLinks(cacheObject)

        val isConversionType = videoService.isConversionObject(cacheObject)

        // Non-conversion type and already in cache
        if (!isConversionType && objectLinks != null ) {
            return RenderDataResponse(objectLinks = objectLinks, module = module())
        }

        // Missing qualities only apply to  conversion objects
        var missingQualities: Collection<Int> = emptyList()
        if (isConversionType) {
            missingQualities = videoService.getMissingQualities(objectLinks)
            if (missingQualities.isEmpty()) {
                // None missing, so no further action is needed
                return RenderDataResponse(objectLinks = objectLinks, module = module())
            }
        }

        val jobId = videoService.retrieveOrCreateJob(cacheObject, module(), missingQualities)
        return RenderDataResponse(objectLinks = objectLinks, jobId = jobId, module = module())
    }

    override fun getObjectLinkFromJobData(subJob: SubJob, renderingJob: RenderingJob): ObjectLink? {
        val cacheObject = mapper.renderingJobToCacheObject(renderingJob)
        val links = videoService.getObjectLinks(cacheObject, subJob.quality)
        return links?.get(0)
    }

    override fun getNodePermissionExpirationTime() = nodePermissionExpirationTime

    override fun moduleTypeAssociations() =
        listOf(ModuleTypeDefinition(mimeTypePrefix =  "video") to this)

    override fun createJob(
        renderingJob: RenderingJob,
        message: RenderingJobMessage
    ) {
        message.missingQualities
            .map { it to configuredResolutions.getPriority(it,0) }
            .sortedByDescending { it.second }
            .forEach { (resolution, priority) ->
                val avJob = SubJob(routingKey = avRoutingKey, quality = resolution, parent = renderingJob)
                renderingJob.subJobs.add(avJob)
                subJobRepository.save(avJob)
                amqpTemplate.convertAndSend(
                    topicExchangeName,
                    avRoutingKey,
                    SubJobMessage(renderingJob.id.toString(), resolution),
                    PriorityPostProcessor(priority)
                )
        }
    }
}
