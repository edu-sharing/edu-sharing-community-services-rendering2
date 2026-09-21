package org.edu_sharing.rendering.core.dto

data class RenderDataResponse(
    var objectLinks: List<ObjectLink>? = null,
    var jobId: String? = null,
    var module: String? = null,
    // True when the module deferred the (expiring) link fetch: no jobId/objectLinks are returned
    // yet; the client fetches on demand via POST /public/renderdata/ondemand (see fetchOnDemand).
    var deferred: Boolean = false,
    /**
     * False when this service does not render the node at all — a plain link, a video embed, an
     * LTI launch, a type no module claims. The client renders it itself. Such a response carries
     * no objectLinks, no jobId and `deferred = false`;
     * */
    var supportedByBackend: Boolean = true,
)
