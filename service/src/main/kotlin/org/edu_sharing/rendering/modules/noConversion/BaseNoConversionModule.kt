package org.edu_sharing.rendering.modules.noConversion

import org.edu_sharing.generated.repository.backend.services.rest.client.model.Node
import org.edu_sharing.rendering.core.dto.ObjectLink
import org.edu_sharing.rendering.core.dto.RenderDataResponse
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.core.exception.ResourceNotFoundException
import org.edu_sharing.rendering.modules.RenderModule
import org.edu_sharing.rendering.renderingJob.MainJobCreationService
import org.edu_sharing.rendering.renderingJob.entity.RenderingJobStatus
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.edu_sharing.rendering.storage.StorageService
import org.slf4j.LoggerFactory

abstract class BaseNoConversionModule(
    private val nodePermissionExpirationTime: Long?,
    private val mapper: Mapper,
    private val storageService: StorageService,
    private val mainJobCreationService: MainJobCreationService
) : RenderModule {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun handle(node: Node): RenderDataResponse {
        log.debug("Handling node ${node.ref.id} via module ${module()}, checking storage cache")
        val cacheObject = mapper.nodeToCacheObject(node)
        val link = try {
            storageService.getObjectLink(cacheObject).first
        } catch (_: ResourceNotFoundException) {
            null
        }
        var jobId: String? = null
        if (link == null) {
            jobId = mainJobCreationService.getExistingJobId(cacheObject)
                ?: mainJobCreationService.createMainJob(cacheObject, module())
            log.debug("Node ${node.ref.id} not cached, dispatching job $jobId")
        } else {
            log.debug("Node ${node.ref.id} found in cache, returning immediate link")
        }

        return RenderDataResponse(
            objectLinks = if (link == null) null else listOf(link),
            module = module(),
            jobId = jobId,
        )
    }

    override fun getNodePermissionExpirationTime() = nodePermissionExpirationTime

    override fun getObjectLinkFromJobData(subJob: SubJob, renderingJob: RenderingJob): ObjectLink? {
        if (renderingJob.status != RenderingJobStatus.FINISHED) return null
        val cacheObject = mapper.renderingJobToCacheObject(renderingJob)
        return storageService.getObjectLink(cacheObject).first
    }
}
