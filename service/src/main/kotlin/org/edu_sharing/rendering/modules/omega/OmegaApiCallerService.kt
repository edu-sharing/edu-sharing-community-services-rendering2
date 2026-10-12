package org.edu_sharing.rendering.modules.omega

import org.edu_sharing.rendering.core.annotation.ConditionalOnConverter
import org.edu_sharing.rendering.core.resilience.RemoteCallGuardRegistry
import org.edu_sharing.rendering.utils.RemoteResponseParser
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import tools.jackson.databind.ObjectMapper
import java.time.Duration

@Service
@ConditionalOnConverter
class OmegaApiCallerService(
    // The Omega API host (cp.sodis.de) sends a fatal-to-the-JDK `unrecognized_name` TLS warning when
    // SNI is present, so the API call must go through the SNI-suppressing builder. The import variant
    // additionally uses the larger import connection pool. See WebClientConfig.
    @param:Qualifier("omegaNoSniRemoteWebClientBuilder") private val noSniWebClientBuilder: WebClient.Builder,
    // SNI-enabled import builder for the stream/download URL validation — those hosts (edupool)
    // *require* SNI and would fail the handshake without it; the import pool keeps validation from
    // bottlenecking on the default 16-connection pool under high import concurrency.
    @param:Qualifier("omegaRemoteWebClientBuilder") private val webClientBuilder: WebClient.Builder,
    private val objectMapper: ObjectMapper,
    guardRegistry: RemoteCallGuardRegistry,
) {
    private companion object {
        val CALL_TIMEOUT: Duration = Duration.ofSeconds(60)
        val VALIDATION_TIMEOUT: Duration = Duration.ofSeconds(30)

        /** Statuses on which a server typically rejects HEAD although the resource itself is fine. */
        val HEAD_REJECTED_STATUSES = setOf(403, 405, 501)
    }

    private val log = LoggerFactory.getLogger(javaClass)

    // The API host and the (user-controlled, third-party) media host fail independently: separate breakers.
    private val guard = guardRegistry.guard("omega")
    private val validationGuard = guardRegistry.guard("omega-validation")

    fun getContentUrl(
        omegaJobMessage: OmegaJobMessage,
        module: OmegaRenderModule,
        repoId: String
    ): Pair<String, String?> {
        val config = module.getCredentials(repoId)
        val user = config["user"] ?: "dabiplus"
        log.debug("Calling Omega API for nodeId ${omegaJobMessage.nodeId}, identifier ${omegaJobMessage.identifier}, role ${omegaJobMessage.role}")
        val webClient = noSniWebClientBuilder
            .clone()
            .baseUrl(config["baseurl"] ?: "")
            .build()

        val result = guard.block(retryable = true) {
            webClient.get()
            .uri {
                it.queryParam("token_id", omegaJobMessage.identifier)
                    .queryParam("role", omegaJobMessage.role)
                    .queryParam("user", user)
                    .build()
            }
            .retrieve()
            .bodyToMono<String>()
            .timeout(CALL_TIMEOUT)
        }
            .let { RemoteResponseParser.parse(it, OmegaApiResponse::class.java, objectMapper, "Omega API") }

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
            // Validate the resolved (edupool) URL with a normal, SNI-enabled client — that host requires
            // SNI, unlike the Omega API host above.
            val validationClient = webClientBuilder.clone().build()
            var status = validationGuard.block(retryable = true) {
                validationClient.head()
                    .uri(streamUrl)
                    .exchangeToMono { Mono.just(it.statusCode().value()) }
                    .timeout(VALIDATION_TIMEOUT)
            } ?: 0
            if (status in HEAD_REJECTED_STATUSES) {
                // Some hosts answer HEAD with 405/403 but serve GET; probe with a one-byte range request.
                log.debug("Omega URL validation: HEAD answered {}, retrying with GET Range", status)
                status = validationGuard.block(retryable = true) {
                    validationClient.get()
                        .uri(streamUrl)
                        .header(HttpHeaders.RANGE, "bytes=0-0")
                        // The body is never read; releasing it closes the connection instead of downloading the file.
                        .exchangeToMono { response -> response.releaseBody().thenReturn(response.statusCode().value()) }
                        .timeout(VALIDATION_TIMEOUT)
                } ?: 0
            }
            if (status > 299) {
                throw Exception("given streamURL is invalid, status $status")
            }
        }

        log.debug("Omega API resolved stream URL for nodeId ${omegaJobMessage.nodeId}, downloadUrl present: ${downloadUrl.isNotEmpty()}")
        return Pair(streamUrl, downloadUrl.ifEmpty { null })
    }
}
