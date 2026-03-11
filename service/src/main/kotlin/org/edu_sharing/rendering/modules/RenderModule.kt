package org.edu_sharing.rendering.modules

import org.edu_sharing.generated.repository.backend.services.rest.client.model.Node
import org.edu_sharing.rendering.core.dto.ObjectLink
import org.edu_sharing.rendering.core.dto.RenderDataResponse
import org.edu_sharing.rendering.core.dto.RequestUserData
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.SubJob

interface RenderModule {
    fun module(): String
    fun handle(node: Node, userData: RequestUserData): RenderDataResponse
    fun getObjectLinkFromJobData(subJob: SubJob, renderingJob: RenderingJob): ObjectLink? = null
    fun getAdditionalDataFromSubJob(subJob: SubJob): Map<String, String>? = null
    fun getNodePermissionExpirationTime(): Long? = null
    fun isOptionalModule(): Boolean = false
    fun getCspHeader(repoId: String): String? = null
    fun getClientSettings(repoId: String): Map<String, String> = emptyMap()
}
