package org.edu_sharing.rendering.dto

data class RenderDataResponse(
    val objectLinks: List<ObjectLink>? = null,
    val jobId: String? = null,
    var module: RenderModules = RenderModules.IMAGE
)
