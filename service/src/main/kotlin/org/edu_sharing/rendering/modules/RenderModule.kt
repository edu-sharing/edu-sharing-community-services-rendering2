package org.edu_sharing.rendering.modules

import org.edu_sharing.generated.repository.backend.services.rest.client.model.Node
import org.edu_sharing.rendering.core.dto.ObjectLink
import org.edu_sharing.rendering.core.dto.RenderDataResponse
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.SubJob

interface RenderModule {
    fun module(): String
    fun handle(node: Node): RenderDataResponse
    fun getObjectLinkFromJobData(subJob: SubJob, renderingJob: RenderingJob): ObjectLink? = null
    fun getNodePermissionExpirationTime(): Long?
    fun isOptionalModule(): Boolean = false
}
