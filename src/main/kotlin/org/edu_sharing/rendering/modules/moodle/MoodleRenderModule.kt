package org.edu_sharing.rendering.modules.moodle

import org.edu_sharing.rendering.dto.ObjectLink
import org.edu_sharing.rendering.dto.RenderDataRequest
import org.edu_sharing.rendering.dto.RenderDataResponse
import org.edu_sharing.rendering.dto.RenderModules
import org.edu_sharing.rendering.entity.RenderingJob
import org.edu_sharing.rendering.entity.SubJob
import org.edu_sharing.rendering.modules.RenderModule
import org.springframework.stereotype.Component

@Component
class MoodleRenderModule(private val moodleJobService: MoodleJobService) : RenderModule {
    override fun module() = RenderModules.MOODLE

    override fun handle(request: RenderDataRequest) : RenderDataResponse {
        return RenderDataResponse(
            module = module(),
            objectLinks = mutableListOf(),
            jobId = moodleJobService.createJob(request, module())
        )
    }

    override fun getObjectLinkFromJobData(subJob: SubJob, renderingJob: RenderingJob): ObjectLink? {
        return ObjectLink(link = subJob.message ?: "")
    }
}
