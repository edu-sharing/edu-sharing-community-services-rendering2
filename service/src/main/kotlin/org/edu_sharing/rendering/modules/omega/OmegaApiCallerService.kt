package org.edu_sharing.rendering.modules.omega

import org.edu_sharing.rendering.core.annotation.ConditionalOnConverter
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono

@Service
@ConditionalOnConverter
class OmegaApiCallerService(
    private val webClientBuilder: WebClient.Builder
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun getContentUrl(
        omegaJobMessage: OmegaJobMessage,
        module: OmegaRenderModule,
        repoId: String
    ): Pair<String, String?> {
        val config = module.getCredentials(repoId)
        val user = config["user"] ?: "dabiplus"
        log.debug("Calling Omega API for nodeId ${omegaJobMessage.nodeId}, identifier ${omegaJobMessage.identifier}, role ${omegaJobMessage.role}")
        val webClient = webClientBuilder
            .clone()
            .baseUrl(config["baseurl"] ?: "")
            .build()

        val result = webClient.get()
            .uri {
                it.queryParam("token_id", omegaJobMessage.identifier)
                    .queryParam("role", omegaJobMessage.role)
                    .queryParam("user", user)
                    .build()
            }
            .retrieve()
            .bodyToMono<OmegaApiResponse>()
            .block()

        if (result == null) {
            throw Exception("Omega API response is empty for node: ${omegaJobMessage.nodeId}")
        }

        val get = result.get

        if (get.identifier != omegaJobMessage.identifier) {
            throw Exception("Wrong identifier")
        }
        if (!get.error.isNullOrEmpty()) {
            throw Exception(get.error)
        }

        var streamUrl = get.streamURL ?: ""
        var downloadUrl = get.downloadURL ?: ""

        if (streamUrl.startsWith("problem:") && downloadUrl.startsWith("problem:")
            && downloadUrl.contains("no right to download")) {
            throw Exception("no right to download")
        }
        if (streamUrl.startsWith("problem:")) streamUrl = ""
        if (downloadUrl.startsWith("problem:")) downloadUrl = ""

        if (streamUrl.isEmpty() && downloadUrl.isNotEmpty()) {
            streamUrl = downloadUrl
        }
        if (streamUrl.isEmpty() && downloadUrl.isEmpty()) {
            throw Exception("urls empty")
        }

        if (config["validateUrls"] != "false") {
            val status = webClient.head()
                .uri(streamUrl)
                .exchangeToMono { Mono.just(it.statusCode().value()) }
                .block() ?: 0
            if (status > 299) {
                throw Exception("given streamURL is invalid, status $status")
            }
        }

        log.debug("Omega API resolved stream URL for nodeId ${omegaJobMessage.nodeId}, downloadUrl present: ${downloadUrl.isNotEmpty()}")
        return Pair(streamUrl, downloadUrl.ifEmpty { null })
    }
}
