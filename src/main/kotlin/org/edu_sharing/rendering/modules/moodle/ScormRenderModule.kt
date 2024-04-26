package org.edu_sharing.rendering.modules.moodle

import org.edu_sharing.rendering.dto.RenderModules
import org.edu_sharing.rendering.service.MoodleService
import org.springframework.stereotype.Component

@Component
class ScormRenderModule(moodleService: MoodleService) : MoodleRenderModule(moodleService) {
    override fun module() = RenderModules.SCORM
}
