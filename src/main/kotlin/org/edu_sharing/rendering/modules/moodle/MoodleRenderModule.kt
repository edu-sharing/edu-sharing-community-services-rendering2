package org.edu_sharing.rendering.modules.moodle

import org.edu_sharing.rendering.dto.RenderDataRequest
import org.edu_sharing.rendering.dto.RenderDataResponse
import org.edu_sharing.rendering.dto.RenderModules
import org.edu_sharing.rendering.modules.RenderModule
import org.edu_sharing.rendering.service.MoodleService
import org.springframework.stereotype.Component

@Component
class MoodleRenderModule(private val moodleService: MoodleService) : RenderModule {
    override fun module() = RenderModules.MOODLE

    override fun handle(request: RenderDataRequest) : RenderDataResponse {
        return RenderDataResponse(
            module = module(),
            objectLinks =  mutableListOf(),
            jobId = moodleService.createJob(request))
    }
}
