package org.edu_sharing.rendering.core.dto

import org.edu_sharing.rendering.modules.RenderModules

data class RenderDataResponse(
    var objectLinks: List<ObjectLink>? = null,
    var jobId: String? = null,
    var module: String? = null
)
