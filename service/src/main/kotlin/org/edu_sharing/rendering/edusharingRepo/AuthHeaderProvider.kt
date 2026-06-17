package org.edu_sharing.rendering.edusharingRepo

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.*

@Component
class AuthHeaderProvider(
    @param:Value($$"${app.appId}")
    private val appId: String,
    private val encryptionService: EncryptionService
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun getAuthHeaders(repoId: String): Map<String, String> {
        log.debug("Building app-auth headers for repoId=$repoId using appId=$appId")
        val ts = System.currentTimeMillis()
        val toSign = "$appId$ts"
        val sig = encryptionService.sign(toSign, repoId)
        return mapOf(
            "X-Edu-App-Id" to appId,
            "X-Edu-App-Signed" to toSign,
            "X-Edu-App-Sig" to Base64.getEncoder().encodeToString(sig),
            "X-Edu-App-Ts" to ts.toString(),
            "X-Edu-App-SignedAlg" to encryptionService.getSigningAlg(repoId)
        )
    }
}
