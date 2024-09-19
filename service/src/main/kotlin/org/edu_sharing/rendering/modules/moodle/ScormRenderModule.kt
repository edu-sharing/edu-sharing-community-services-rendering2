package org.edu_sharing.rendering.modules.moodle

import org.edu_sharing.rendering.modules.RenderModules
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class ScormRenderModule(
    @Value("\${app.session.scorm.nodePermissionExpirationTime}")
    private val nodePermissionExpirationTime: Long?,
    moodleJobService: MoodleJobService
) : MoodleRenderModule(nodePermissionExpirationTime, moodleJobService) {
    override fun module() = RenderModules.SCORM
    override fun getRemoteServiceMethod() = "scorm"
}
