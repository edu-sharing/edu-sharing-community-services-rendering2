package org.edu_sharing.rendering.modules.image

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
import org.edu_sharing.rendering.renderingJob.queue.RenderingJobMessage
import org.edu_sharing.rendering.renderingJob.queue.SubJobMessage
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.springframework.amqp.core.AmqpTemplate
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class ImageRenderModule(
    @Value("\${app.session.image.nodePermissionExpirationTime}")
    private val nodePermissionExpirationTime: Long?,
    private val mapper: Mapper,
    private val imageService: ImageService,
    private val subJobRepository: SubJobRepository,
    private val amqpTemplate: AmqpTemplate
) : RenderModule, ConversionModule {

    @Value("\${app.queue.image.key}")
    lateinit var imageRoutingKey: String

    @Value("\${app.queue.topicExchange}")
    lateinit var topicExchangeName: String

    override fun module() = "IMAGE"

    override fun handle(request: RenderDataRequest): RenderDataResponse {
        val cacheObject = mapper.renderDataRequestToCacheObject(request)
        val objectLinks = imageService.getObjectLinks(cacheObject)
        val isConversionType = imageService.isConversionObject(cacheObject)

        // Non-conversion type and already in cache
        if (!isConversionType && objectLinks != null ) {
            return RenderDataResponse(objectLinks = objectLinks, module = module())
        }

        // Missing qualities only apply to  conversion objects
        var missingQualities: List<Int> = emptyList()
        if (isConversionType) {
            missingQualities = imageService.getMissingQualities(objectLinks)
            if (missingQualities.isEmpty()) {
                // None missing, so no further action is needed
                return RenderDataResponse(objectLinks = objectLinks, module = module())
            }
        }

        val jobId = imageService.retrieveOrCreateJob(cacheObject, module(), missingQualities)
        return RenderDataResponse(objectLinks = objectLinks, jobId = jobId, module = module())
    }

    override fun getObjectLinkFromJobData(subJob: SubJob, renderingJob: RenderingJob): ObjectLink? {
        val cacheObject = mapper.renderingJobToCacheObject(renderingJob)
        val links = imageService.getObjectLinks(cacheObject, subJob.quality)
        return links?.get(0)
    }

    override fun getNodePermissionExpirationTime() = nodePermissionExpirationTime

    override fun createJob(
        renderingJob: RenderingJob,
        message: RenderingJobMessage
    ) {
        message.missingQualities.forEach {
            val imageJob = SubJob(routingKey = imageRoutingKey, quality = it, parent = renderingJob)
            renderingJob.subJobs.add(imageJob)
            subJobRepository.save(imageJob)
        }
        amqpTemplate.convertAndSend(topicExchangeName, imageRoutingKey, SubJobMessage(renderingJob.id.toString()))
    }
}
