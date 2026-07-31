package org.edu_sharing.rendering.modules

import org.edu_sharing.generated.repository.backend.services.rest.client.model.Node
import org.edu_sharing.rendering.core.dto.ObjectLink
import org.edu_sharing.rendering.core.dto.RenderDataResponse
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.SubJob

interface RenderModule {
    fun module(): String
    fun handle(node: Node): RenderDataResponse

    /** Whether this module's produced links expire and can be re-requested for an existing job. */
    fun supportsLinkRefresh(): Boolean = false

    /** Re-trigger production of fresh links for an existing job (default: unsupported). */
    fun refreshLinks(renderingJob: RenderingJob): Unit =
        throw IllegalArgumentException("Link refresh not supported for module ${module()}")

    /**
     * Whether [handle] should defer producing this node's (expiring) links until the client asks
     * for them (see [fetchOnDemand]) instead of fetching eagerly — e.g. Sodix content that is only
     * downloaded/opened on a button click rather than embedded. Default: fetch eagerly.
     */
    fun rendersDeferred(node: Node): Boolean = false

    /**
     * Produce render data by fetching now, bypassing any deferral decision made in [handle]. Called
     * by the on-demand endpoint when the client actually needs the links. Defaults to [handle] for
     * modules that never defer.
     */
    fun fetchOnDemand(node: Node): RenderDataResponse = handle(node)

    fun getObjectLinkFromJobData(subJob: SubJob, renderingJob: RenderingJob): ObjectLink? = null
    fun getAdditionalData(subJob: SubJob, repoId: String): Map<String, String>? = null
    fun getNodePermissionExpirationTime(): Long? = null
    fun isOptionalModule(): Boolean = false
    fun getCspHeader(repoId: String): String? = null

    /**
     * When true, a node matched via ccm:replicationsource is skipped if it has local content,
     * letting dispatch fall through to mimetype resolution (see OMEGA / FWU).
     */
    fun fallsThroughOnLocalContent(): Boolean = false
}
