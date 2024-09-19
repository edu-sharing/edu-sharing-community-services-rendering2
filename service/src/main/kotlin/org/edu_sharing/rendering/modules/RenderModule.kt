package org.edu_sharing.rendering.modules

import org.edu_sharing.rendering.dto.ObjectLink
import org.edu_sharing.rendering.dto.RenderDataRequest
import org.edu_sharing.rendering.dto.RenderDataResponse
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.SubJob

interface RenderModule {
    fun module(): RenderModules
    fun handle(request: RenderDataRequest): RenderDataResponse
    fun getObjectLinkFromJobData(subJob: SubJob, renderingJob: RenderingJob): ObjectLink? = null
    fun getNodePermissionExpirationTime(): Long?
}
