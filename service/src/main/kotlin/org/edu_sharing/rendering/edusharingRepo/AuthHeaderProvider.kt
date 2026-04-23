package org.edu_sharing.rendering.edusharingRepo

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.*

@Component
class AuthHeaderProvider(
    @Value("\${app.appId}")
    private val appId: String,
    private val encryptionService: EncryptionService
) {
    fun getAuthHeaders(): Map<String, String> {
        val ts = System.currentTimeMillis()
        val toSign = "$appId$ts"
        val sig = encryptionService.sign(toSign)
        return mapOf(
            "X-Edu-App-Id" to appId,
            "X-Edu-App-Signed" to toSign,
            "X-Edu-App-Sig" to Base64.getEncoder().encodeToString(sig),
            "X-Edu-App-Ts" to ts.toString(),
            "X-Edu-App-SignedAlg" to encryptionService.signingAlg
        )
    }
}
