package org.edu_sharing.rendering.edusharingRepo

import org.edu_sharing.rendering.edusharingRepo.repository.RepositoryRegistrationRepository
import org.edu_sharing.rendering.security.jwt.JWTBasedUserDetail
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service

@Service
class EduTrackingService(
    private val restClientProvider: RestClientProvider,
    private val repositoryRegistrationRepository: RepositoryRegistrationRepository,
    private val authHeaderProvider: AuthHeaderProvider,
    ) {

    private val log = org.slf4j.LoggerFactory.getLogger(javaClass)

    @Throws(IllegalArgumentException::class)
    fun trackObject(event: String, objectId: String, repoId: String) {
        val registration = repositoryRegistrationRepository.findByRepoId(repoId).orElseThrow { IllegalArgumentException("Repository not found") }
        val authentication = SecurityContextHolder.getContext().authentication
        val userDetails = authentication.principal as JWTBasedUserDetail
        val jwtIssuer = userDetails.username
        val headers = authHeaderProvider.getAuthHeaders().toMutableMap()
        headers["X-Edu-User-Id"] = jwtIssuer
        val client = restClientProvider.getTrackingApiClient(registration.url, headers)
        log.info("Tracking event $event for object $objectId in repo $repoId. Currently deactivated pending fix in repo.")
        //client.trackEventAsync(repoId, event, objectId, null)
    }
}
