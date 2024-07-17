package org.edu_sharing.rendering.modules

import org.edu_sharing.rendering.dto.ObjectLink
import org.edu_sharing.rendering.dto.RenderDataRequest
import org.edu_sharing.rendering.dto.RenderDataResponse
import org.edu_sharing.rendering.dto.RenderModules
import org.edu_sharing.rendering.entity.RenderingJob
import org.edu_sharing.rendering.entity.SubJob

interface RenderModule {
    fun module(): RenderModules
    fun handle(request: RenderDataRequest): RenderDataResponse
    fun getObjectLinkFromJobData(subJob: SubJob, renderingJob: RenderingJob): ObjectLink?
    fun getNodePermissionExpirationTime(): Long?
}
