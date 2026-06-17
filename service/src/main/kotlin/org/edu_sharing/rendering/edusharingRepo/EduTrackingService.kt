package org.edu_sharing.rendering.edusharingRepo

import org.edu_sharing.generated.repository.backend.services.rest.client.ApiCallback
import org.edu_sharing.generated.repository.backend.services.rest.client.ApiException
import org.edu_sharing.rendering.core.annotation.ConditionalOnController
import org.edu_sharing.rendering.edusharingRepo.repository.RepositoryRegistrationRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Service
@ConditionalOnController
class EduTrackingService(
    private val userBasedRestClientProvider: UserBasedRestClientProvider,
    private val repositoryRegistrationRepository: RepositoryRegistrationRepository,
    @param:Value($$"${app.security.enabled}")
    private val securityEnabled: Boolean,
) {

    private val log = org.slf4j.LoggerFactory.getLogger(javaClass)

    @Throws(IllegalArgumentException::class)
    fun trackObject(event: String, objectId: String, repoId: String) {
        log.debug("trackObject called: event=$event, objectId=$objectId, repoId=$repoId")
        if (event == "PRERENDER" || !securityEnabled) {
            log.debug("Skipping tracking for event=$event (PRERENDER or security disabled)")
            return
        }
        val registration = repositoryRegistrationRepository.findByRepoId(repoId)
            .orElseThrow { IllegalArgumentException("Repository not found") }

        log.debug("Dispatching async tracking request to ${registration.url} for event=$event, objectId=$objectId")
        val client = userBasedRestClientProvider.getTrackingApiClient(registration.url, repoId)
        client.trackEventAsync(repoId, event, objectId, object : ApiCallback<Void> {

            override fun onFailure(e: ApiException?, statusCode: Int, responseHeaders: Map<String?, List<String?>?>?) {
                log.error("Failed to track event $event for object $objectId in repo $repoId", e)
            }

            override fun onSuccess(
                result: Void?,
                statusCode: Int,
                responseHeaders: MutableMap<String, MutableList<String>>?
            ) {
                log.debug("Successfully tracked event $event for object $objectId in repo $repoId")
            }

            override fun onUploadProgress(bytesWritten: Long, contentLength: Long, done: Boolean) {}

            override fun onDownloadProgress(bytesRead: Long, contentLength: Long, done: Boolean) {}
        })
    }
}
