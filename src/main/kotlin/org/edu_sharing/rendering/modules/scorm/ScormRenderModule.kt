package org.edu_sharing.rendering.modules.scorm

import org.edu_sharing.rendering.dto.RenderModules
import org.edu_sharing.rendering.modules.moodle.MoodleJobService
import org.edu_sharing.rendering.modules.moodle.MoodleRenderModule
import org.springframework.stereotype.Component

@Component
class ScormRenderModule(moodleJobService: MoodleJobService) : MoodleRenderModule(moodleJobService) {
    override fun module() = RenderModules.SCORM
}
