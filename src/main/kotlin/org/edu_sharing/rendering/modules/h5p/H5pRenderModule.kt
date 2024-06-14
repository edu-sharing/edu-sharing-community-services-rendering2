package org.edu_sharing.rendering.modules.h5p

import org.edu_sharing.rendering.dto.ObjectLink
import org.edu_sharing.rendering.dto.RenderDataRequest
import org.edu_sharing.rendering.dto.RenderDataResponse
import org.edu_sharing.rendering.dto.RenderModules
import org.edu_sharing.rendering.entity.RenderingJob
import org.edu_sharing.rendering.entity.SubJob
import org.edu_sharing.rendering.modules.RenderModule
import org.springframework.stereotype.Component

@Component
class H5pRenderModule(private val h5pJobService: H5pJobService): RenderModule {
    override fun module() = RenderModules.H5P


    override fun handle(request: RenderDataRequest): RenderDataResponse {
        TODO("Not yet implemented")
    }

    override fun getObjectLinkFromJobData(subJob: SubJob, renderingJob: RenderingJob): ObjectLink? {
        TODO("Not yet implemented")
    }
}