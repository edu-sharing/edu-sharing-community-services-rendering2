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
    fun getAdditionalData(subJob: SubJob, repoId: String): Map<String, String>? = null

    /**
     * All object links currently cached for this job's node, independent of which sub-jobs this
     * job tracks. A sub-job is only created for a quality that was still missing at job-creation
     * time, so a quality that was already cached back then never gets one - if the sub-job for a
     * different, newly requested quality later fails, that quality must still surface here.
     */
    fun getAvailableObjectLinks(renderingJob: RenderingJob): List<ObjectLink>? = null
    fun getNodePermissionExpirationTime(): Long? = null
    fun isOptionalModule(): Boolean = false
    fun getCspHeader(repoId: String): String? = null

    /**
     * When true, a node matched via ccm:replicationsource is skipped if it has local content,
     * letting dispatch fall through to mimetype resolution (see OMEGA / FWU).
     */
    fun fallsThroughOnLocalContent(): Boolean = false
}
