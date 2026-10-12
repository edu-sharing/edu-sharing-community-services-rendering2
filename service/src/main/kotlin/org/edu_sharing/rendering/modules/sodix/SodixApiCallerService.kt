package org.edu_sharing.rendering.modules.sodix

import org.edu_sharing.rendering.core.annotation.ConditionalOnConverter
import org.edu_sharing.rendering.core.resilience.RemoteCallGuardRegistry
import org.edu_sharing.rendering.utils.RemoteResponseParser
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import tools.jackson.databind.ObjectMapper
import java.time.Duration

@Service
@ConditionalOnConverter
class SodixApiCallerService(
    @param:Qualifier("sodixRemoteWebClientBuilder") private val webClientBuilder: WebClient.Builder,
    private val objectMapper: ObjectMapper,
    guardRegistry: RemoteCallGuardRegistry,
) {
    private companion object {
        val CALL_TIMEOUT: Duration = Duration.ofSeconds(60)
    }

    private val log = LoggerFactory.getLogger(javaClass)
    private val guard = guardRegistry.guard("sodix")

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

        val result = guard.block(retryable = true) {
            webClient.get()
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
            .bodyToMono<String>()
            .timeout(CALL_TIMEOUT)
        }
            .let { RemoteResponseParser.parse(it, SodixApiResponse::class.java, objectMapper, "Sodix API") }

        log.debug("Sodix API returned playoutUrl for nodeId ${sodixJobMessage.nodeId}, downloadUrl present: ${result.downloadUrl != null}")
        return Pair(result.playoutUrl, result.downloadUrl)
    }
}
