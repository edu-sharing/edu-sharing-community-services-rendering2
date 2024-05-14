package org.edu_sharing.rendering.modules.document

import org.edu_sharing.rendering.dto.ObjectLink
import org.edu_sharing.rendering.dto.RenderDataRequest
import org.edu_sharing.rendering.dto.RenderDataResponse
import org.edu_sharing.rendering.dto.RenderModules
import org.edu_sharing.rendering.dto.mapper.Mapper
import org.edu_sharing.rendering.entity.RenderingJob
import org.edu_sharing.rendering.entity.SubJob
import org.edu_sharing.rendering.modules.RenderModule
import org.springframework.http.MediaType
import org.springframework.stereotype.Component

@Component
class DocumentRenderModule(
    private val mapper: Mapper,
    private val documentService: DocumentService
): RenderModule {
    override fun module() = RenderModules.DOCUMENT

    override fun handle(request: RenderDataRequest): RenderDataResponse {
        val cacheObject = mapper.renderDataRequestToCacheObject(request)
        val objectLinks = documentService.getObjectLinks(cacheObject, this)
        if (objectLinks !== null) return RenderDataResponse(objectLinks = objectLinks, module = module())
        return RenderDataResponse(jobId = documentService.retrieveOrCreateJob(cacheObject, this), module = module())
    }

    override fun getObjectLinkFromJobData(subJob: SubJob, renderingJob: RenderingJob): ObjectLink? {
        val cacheObject = mapper.renderingJobToCacheObject(renderingJob)
        val links = documentService.getObjectLinks(cacheObject, this)
        return links?.get(0)
    }

    fun getTargetMimetype() = MediaType.APPLICATION_PDF_VALUE
}