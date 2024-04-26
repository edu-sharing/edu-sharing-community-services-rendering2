package org.edu_sharing.rendering.modules.default

import org.edu_sharing.rendering.dto.RenderDataRequest
import org.edu_sharing.rendering.dto.RenderDataResponse
import org.edu_sharing.rendering.dto.RenderModules
import org.edu_sharing.rendering.modules.RenderModule
import org.springframework.stereotype.Component

@Component
class DefaultRenderModule : RenderModule {
    override fun module() = RenderModules.DEFAULT
    override fun handle(request: RenderDataRequest): RenderDataResponse {
        TODO("Not yet implemented")
    }
}
