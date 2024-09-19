package org.edu_sharing.rendering.modules

import org.edu_sharing.rendering.core.dto.ObjectLink
import org.edu_sharing.rendering.core.dto.RenderDataRequest
import org.edu_sharing.rendering.core.dto.RenderDataResponse
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.SubJob

interface RenderModule {
    fun module(): String
    fun handle(request: RenderDataRequest): RenderDataResponse
    fun getObjectLinkFromJobData(subJob: SubJob, renderingJob: RenderingJob): ObjectLink? = null
    fun getNodePermissionExpirationTime(): Long?
}
