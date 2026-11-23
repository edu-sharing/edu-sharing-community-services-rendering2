package org.edu_sharing.rendering.core

import org.edu_sharing.rendering.core.annotation.ConditionalOnController
import org.edu_sharing.rendering.core.dto.VersionResponse
import org.edu_sharing.rendering.edusharingRepo.services.RepositorySignatureVerifier
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.info.BuildProperties
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.Base64
import kotlin.math.abs

/**
 * Version of this service for the status report of a registered repository.
 *
 * Only for requests signed by a registered repository, with the app signature headers
 * The signature is verified by [RepositorySignatureVerifier] (key of the registration), like the one of `/public/renderdata`.
 */
@RestController
@ConditionalOnController
@RequestMapping("/public/version")
class VersionController(
    private val repositorySignatureVerifier: RepositorySignatureVerifier,
    private val buildProperties: ObjectProvider<BuildProperties>,
    // same default as the repository (ApplicationInfo.DEFAULT_OFFSET_MS)
    @param:Value($$"${app.security.signature-max-offset-ms:10000}")
    private val maxOffsetMs: Long,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getVersion(
        @RequestHeader("X-Edu-App-Id", required = false) appId: String?,
        @RequestHeader("X-Edu-App-Ts", required = false) ts: String?,
        @RequestHeader("X-Edu-App-Signed", required = false) signed: String?,
        @RequestHeader("X-Edu-App-Sig", required = false) sig: String?,
        @RequestHeader("X-Edu-App-SignedAlg", required = false) signedAlg: String?,
    ): ResponseEntity<VersionResponse> {
        val error = verify(appId, ts, signed, sig, signedAlg ?: "SHA512withRSA")
        if (error != null) {
            log.debug("Version request rejected: appId=$appId, reason=$error")
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }
        val build = buildProperties.ifAvailable
        return ResponseEntity.ok(
            VersionResponse(
                version = filtered(build?.version),
                commit = filtered(build?.get("git.commit")),
                imageTag = filtered(build?.get("docker.tag")),
            )
        )
    }

    /**
     * @return null if the request is signed by the registered repository [appId], otherwise the reason
     */
    internal fun verify(appId: String?, ts: String?, signed: String?, sig: String?, signedAlg: String): String? {
        if (appId.isNullOrBlank() || ts.isNullOrBlank() || signed.isNullOrBlank() || sig.isNullOrBlank()) {
            return "missing signature headers"
        }
        val timestamp = ts.toLongOrNull() ?: return "invalid timestamp"
        if (abs(System.currentTimeMillis() - timestamp) > maxOffsetMs) {
            return "timestamp too old or in the future"
        }
        if (signed != appId + ts) {
            return "signed data does not match app id and timestamp"
        }
        return try {
            repositorySignatureVerifier.verify(appId, signed.toByteArray(Charsets.UTF_8), Base64.getDecoder().decode(sig), signedAlg)
            null
        } catch (e: Exception) {
            // algorithm not allowed, unknown repository, broken key or signature
            "signature could not be verified: ${e.message}"
        }
    }

    /**
     * maven leaves unknown properties unresolved (e.g. "${git.commit.id}" if git info is missing)
     */
    private fun filtered(value: String?): String? =
        value?.takeIf { it.isNotBlank() && !it.contains("\${") }
}
