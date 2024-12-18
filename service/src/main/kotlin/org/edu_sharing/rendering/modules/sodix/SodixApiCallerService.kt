package org.edu_sharing.rendering.modules.sodix

import org.edu_sharing.rendering.core.annotation.ConditionalOnConverter
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient

@Service
@ConditionalOnConverter
class SodixApiCallerService {
    fun getContentUrl(sodixJobMessage: SodixJobMessage, module: SodixRenderModule, repoId: String): String {
        val config = module.getConfig(repoId)
        val webClient = WebClient
            .builder()
            .baseUrl(config["baseurl"] ?: "")
            .build()
        val result = webClient.get()
            .uri {
                it.path("/playout")
                    .queryParam("identifier", sodixJobMessage.identifier)
                    .build()
            }
            .retrieve()
            .bodyToMono(SodixApiResponse::class.java)
            .block()

        if (result == null) {
            throw Exception("SodixApiCallerService error, no result returned for node: ${sodixJobMessage.nodeId}")
        }

        return result.url
    }
}