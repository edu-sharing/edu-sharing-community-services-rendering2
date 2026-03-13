package org.edu_sharing.rendering.modules.sodix

import org.edu_sharing.rendering.core.annotation.ConditionalOnConverter
import org.edu_sharing.rendering.security.jwt.JWTBasedUserDetail
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono

@Service
@ConditionalOnConverter
class SodixApiCallerService {
    fun getContentUrl(
        sodixJobMessage: SodixJobMessage,
        module: SodixRenderModule,
        repoId: String
    ): Pair<String, String?> {
        val config = module.getCredentials(repoId)
        val webClient = WebClient
            .builder()
            .baseUrl(config["baseurl"] ?: "")
            .build()

        val result = webClient.get()
            .uri {
                val builder = it
                    .path(if (sodixJobMessage.isPaidMedia) "render/paidmedia" else "render/playout")
                    .queryParam("id", sodixJobMessage.identifier)

                if (sodixJobMessage.isPaidMedia) {
                    val authentication = SecurityContextHolder.getContext().authentication
                    val userDetails = authentication.principal as JWTBasedUserDetail
                    builder.queryParam("role", if (userDetails.primaryAffiliation == "teacher") "TEACHER" else "LEARNER")
                }

                builder.build()
            }
            .retrieve()
            .bodyToMono<SodixApiResponse>()
            .block()

        if (result == null) {
            throw Exception("SodixApiCallerService error, no result returned for node: ${sodixJobMessage.nodeId}")
        }

        return Pair(result.playoutUrl, result.downloadUrl)
    }
}
