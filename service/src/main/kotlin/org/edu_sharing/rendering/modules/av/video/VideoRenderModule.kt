package org.edu_sharing.rendering.modules.av.video

import org.edu_sharing.generated.repository.backend.services.rest.client.model.Node
import org.edu_sharing.rendering.core.dto.ObjectLink
import org.edu_sharing.rendering.core.dto.RenderDataResponse
import org.edu_sharing.rendering.core.dto.RequestUserData
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.modules.ConversionModule
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
): RenderModule, ConversionModule {

    @Value("\${app.queue.av.key}")
    lateinit var avRoutingKey: String

    @Value("\${app.queue.topicExchange}")
    lateinit var topicExchangeName: String


    override fun module() = "VIDEO"

    override fun handle(node: Node, requestUserData: RequestUserData): RenderDataResponse {
        val cacheObject = mapper.nodeToCacheObject(node)

        val originalHeightProperty = node.properties.getOrDefault("ccm:height", listOf(""))[0]
        var originalHeight: Int? = null
        if (originalHeightProperty.isNotBlank()) {
            originalHeight = originalHeightProperty.toFloat().toInt()
        }

        val objectLinks = videoService.getObjectLinks(
            cacheObject = cacheObject,
            originalHeight = originalHeight
        )

        val isConversionType = videoService.isConversionObject(cacheObject, originalHeight)

        // Non-conversion type and already in cache
        if (!isConversionType && objectLinks != null ) {
            return RenderDataResponse(objectLinks = objectLinks, module = module())
        }

        // Missing qualities only apply to  conversion objects
        var missingQualities: Collection<Int> = emptyList()
        if (isConversionType) {
            missingQualities = videoService.getMissingQualities(objectLinks, originalHeight ?: Int.MAX_VALUE)
            if (missingQualities.isEmpty()) {
                // None missing, so no further action is needed
                return RenderDataResponse(objectLinks = objectLinks, module = module())
            }
        }

        val jobId = videoService.retrieveOrCreateJob(
            cacheObject = cacheObject,
            module = module(),
            missingQualities = missingQualities,
            originalHeight = originalHeight
        )
        return RenderDataResponse(objectLinks = objectLinks, jobId = jobId, module = module())
    }

    override fun getObjectLinkFromJobData(subJob: SubJob, renderingJob: RenderingJob): ObjectLink? {
        val cacheObject = mapper.renderingJobToCacheObject(renderingJob)
        val links = videoService.getObjectLinks(cacheObject, subJob.quality)
        return links?.get(0)
    }

    override fun getNodePermissionExpirationTime() = nodePermissionExpirationTime


    override fun createConversionSubJobs(
        renderingJob: RenderingJob,
        message: RenderingJobMessage
    ) {
        message.missingQualities
            .map { it to configuredResolutions.getPriority(it,0) }
            .sortedByDescending { it.second }
            .forEach { (resolution, priority) ->
                val avJob = SubJob(
                    routingKey = avRoutingKey,
                    quality = resolution,
                    parent = renderingJob,
                    priority = priority
                )
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
