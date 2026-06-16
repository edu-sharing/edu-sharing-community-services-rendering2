package org.edu_sharing.rendering.modules.moodle

import org.edu_sharing.rendering.edusharingRepo.services.RepositoryRegistrationStorageService
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class ScormRenderModule(
    @param:Value($$"${app.session.scorm.nodePermissionExpirationTime}")
    private val nodePermissionExpirationTime: Long?,
    moodleJobService: MoodleJobService,
    repositoryRegistrationStorageService: RepositoryRegistrationStorageService,
) : MoodleRenderModule(
    nodePermissionExpirationTime = nodePermissionExpirationTime,
    moodleJobService = moodleJobService,
    repositoryRegistrationStorageService = repositoryRegistrationStorageService
) {
    override fun module() = "SCORM"
    override fun getRemoteServiceMethod() = "scorm"
}
