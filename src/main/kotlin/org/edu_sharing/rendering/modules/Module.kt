package org.edu_sharing.rendering.modules

import org.edu_sharing.rendering.dto.RenderDataRequest
import org.edu_sharing.rendering.dto.RenderDataResponse
import org.edu_sharing.rendering.dto.RenderModules

interface RenderModule {
    fun module(): RenderModules
    fun handle(request: RenderDataRequest): RenderDataResponse
}
