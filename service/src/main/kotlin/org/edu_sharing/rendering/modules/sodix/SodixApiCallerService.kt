package org.edu_sharing.rendering.modules.sodix

import org.edu_sharing.rendering.core.annotation.ConditionalOnConverter
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono

@Service
@ConditionalOnConverter
class SodixApiCallerService(
    private val webClientBuilder: WebClient.Builder
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun getContentUrl(
        sodixJobMessage: SodixJobMessage,
        module: SodixRenderModule,
        repoId: String
    ): Pair<String, String?> {
        val config = module.getCredentials(repoId)
        val endpoint = if (sodixJobMessage.isPaidMedia) "render/paidmedia" else "render/playout"
        log.debug("Calling Sodix API endpoint $endpoint for nodeId ${sodixJobMessage.nodeId}, identifier ${sodixJobMessage.identifier}")
        val webClient = webClientBuilder
            .clone()
            .baseUrl(config["baseurl"] ?: "")
            .build()

        val result = webClient.get()
            .uri {
                val builder = it
                    .path(if (sodixJobMessage.isPaidMedia) "render/paidmedia" else "render/playout")
                    .queryParam("id", sodixJobMessage.identifier)
                if (sodixJobMessage.isPaidMedia) {
                    builder.queryParam("role", if (sodixJobMessage.role == "teacher") "TEACHER" else "LEARNER")
                }
                builder.build()
            }
            .retrieve()
            .bodyToMono<SodixApiResponse>()
            .block()

        if (result == null) {
            throw Exception("SodixApiCallerService error, no result returned for node: ${sodixJobMessage.nodeId}")
        }

        log.debug("Sodix API returned playoutUrl for nodeId ${sodixJobMessage.nodeId}, downloadUrl present: ${result.downloadUrl != null}")
        return Pair(result.playoutUrl, result.downloadUrl)
    }
}
