package org.edu_sharing.rendering.core

import org.edu_sharing.rendering.core.annotation.ConditionalOnController
import org.edu_sharing.rendering.core.dto.VersionResponse
import org.edu_sharing.rendering.edusharingRepo.services.RepositoryAppAssertionVerifier
import org.edu_sharing.rendering.edusharingRepo.services.RepositoryAppAssertionVerifier.InvalidAppAssertionException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Version of this service for the status report of a registered repository.
 *
 * Only for requests of a registered repository, with its app assertion in the header
 * [RepositoryAppAssertionVerifier.HEADER] (verified with the key of the registration).
 */
@RestController
@ConditionalOnController
@RequestMapping("/public/version")
class VersionController(
    private val appAssertionVerifier: RepositoryAppAssertionVerifier,
    private val buildInfoService: BuildInfoService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getVersion(
        @RequestHeader(RepositoryAppAssertionVerifier.HEADER, required = false) assertion: String?,
    ): ResponseEntity<VersionResponse> {
        try {
            val repoId = appAssertionVerifier.verify(assertion)
            log.debug("Version requested by repository {}", repoId)
        } catch (e: InvalidAppAssertionException) {
            log.debug("Version request rejected: {}", e.message)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }
        return ResponseEntity.ok(buildInfoService.getVersion())
    }
}
