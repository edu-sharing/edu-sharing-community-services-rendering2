package org.edu_sharing.rendering.modules.sodix

import org.edu_sharing.rendering.core.annotation.ConditionalOnConverter
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

        // Todo: Add user role (TEACHER, LEARNER) to paid media request (query param)
        val result = webClient.get()
            .uri {
                it.path(if (sodixJobMessage.isPaidMedia) "render/paidmedia" else "render/playout")
                    .queryParam("id", sodixJobMessage.identifier)
                    .build()
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
