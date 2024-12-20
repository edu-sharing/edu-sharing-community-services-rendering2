package org.edu_sharing.rendering.modules.sodix

import org.edu_sharing.rendering.core.annotation.ConditionalOnConverter
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient

@Service
@ConditionalOnConverter
class SodixApiCallerService {
    fun getContentUrl(
        sodixJobMessage: SodixJobMessage,
        module: SodixRenderModule,
        repoId: String,
        isPaidMedia: Boolean
    ): Pair<String, String?> {
        val config = module.getConfig(repoId)
        val webClient = WebClient
            .builder()
            .baseUrl(config["baseurl"] ?: "")
            .build()
        val result = webClient.get()
            .uri {
                it.path(if (isPaidMedia) "render/paidmedia" else "render/playout")
                    .queryParam("id", sodixJobMessage.identifier)
                    .build()
            }
            .retrieve()
            .bodyToMono(SodixApiResponse::class.java)
            .block()

        if (result == null) {
            throw Exception("SodixApiCallerService error, no result returned for node: ${sodixJobMessage.nodeId}")
        }

        return Pair(result.playoutUrl, result.downloadUrl)
    }
}