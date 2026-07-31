package org.edu_sharing.rendering.core.dto

data class RenderDataResponse(
    var objectLinks: List<ObjectLink>? = null,
    var jobId: String? = null,
    var module: String? = null,
    // True when the module deferred the (expiring) link fetch: no jobId/objectLinks are returned
    // yet; the client fetches on demand via POST /public/renderdata/ondemand (see fetchOnDemand).
    var deferred: Boolean = false,
)
