package org.edu_sharing.rendering.modules.moodle

import org.edu_sharing.rendering.edusharingRepo.services.RepositoryRegistrationStorageService
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

@Component
class ScormRenderModule(
    @param:Value($$"${app.session.scorm.nodePermissionExpirationTime}")
    private val nodePermissionExpirationTime: Long?,
    moodleJobService: MoodleJobService,
    repositoryRegistrationStorageService: RepositoryRegistrationStorageService,
    webClientBuilder: WebClient.Builder = WebClient.builder(),
) : MoodleRenderModule(
    nodePermissionExpirationTime = nodePermissionExpirationTime,
    moodleJobService = moodleJobService,
    repositoryRegistrationStorageService = repositoryRegistrationStorageService,
    webClientBuilder = webClientBuilder
) {
    override fun module() = "SCORM"
    override fun getRemoteServiceMethod() = "scorm"
}
