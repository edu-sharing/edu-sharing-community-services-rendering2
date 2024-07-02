package org.edu_sharing.rendering.dto

data class RenderDataResponse(
    var objectLinks: List<ObjectLink>? = null,
    var jobId: String? = null,
    var module: RenderModules = RenderModules.UNKNOWN
)
