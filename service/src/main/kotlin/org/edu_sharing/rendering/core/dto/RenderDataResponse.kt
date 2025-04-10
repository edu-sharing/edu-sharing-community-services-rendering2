package org.edu_sharing.rendering.core.dto

data class RenderDataResponse(
    var objectLinks: List<ObjectLink>? = null,
    var jobId: String? = null,
    var module: String? = null,
)
