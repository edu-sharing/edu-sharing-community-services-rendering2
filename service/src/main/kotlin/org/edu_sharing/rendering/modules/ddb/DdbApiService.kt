package org.edu_sharing.rendering.modules.ddb

import org.edu_sharing.rendering.core.ErrorStrings.GENERIC_CONVERSION_ERROR
import org.edu_sharing.rendering.core.resilience.RemoteCallGuardRegistry
import org.edu_sharing.rendering.modules.ModuleRegistry
import org.edu_sharing.rendering.modules.RenderModule
import org.edu_sharing.rendering.modules.ThirdPartyModule
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.RenderingJobStatus
import org.edu_sharing.rendering.renderingJob.entity.SubJobStatus
import org.edu_sharing.rendering.renderingJob.repository.RenderingJobRepository
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.edu_sharing.rendering.utils.LogSanitizer
import org.springframework.http.HttpHeaders
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.time.Duration
import java.time.Instant

@Service
class DdbApiService(
    private val renderingJobRepository: RenderingJobRepository,
    private val moduleRegistry: ModuleRegistry,
    private val subJobRepository: SubJobRepository,
    @param:Qualifier("ddbRemoteWebClientBuilder") private val webClientBuilder: WebClient.Builder,
    private val ddbApiProperties: DdbApiProperties,
    guardRegistry: RemoteCallGuardRegistry,
) {

    private val log = LoggerFactory.getLogger(this.javaClass)
    private val objectMapper = ObjectMapper()
    private val guard = guardRegistry.guard("ddb")

    private companion object {
        val CALL_TIMEOUT: Duration = Duration.ofSeconds(60)
    }

    private data class DdbRestData(
        val binaryRef: String,
        val institution: String,
        val licenseGroup: String,
        val licenseLink: String
    )

    fun process(
        remoteId: String,
        renderingJob: RenderingJob
    ) {
        log.debug("Starting DDB API process for remoteId $remoteId, jobId ${renderingJob.id}")
        log.info("Starting DDB API communication for node")
        if (renderingJob.subJobs.isEmpty()) {
            renderingJob.status = RenderingJobStatus.FAILED
            renderingJobRepository.save(renderingJob)
            return
        }
        var subJob = renderingJob.subJobs.first()
        try {
            subJob.status = SubJobStatus.PROCESSING
            subJob.processingStartedDate = Instant.now()
            subJob.message = "Calling DDB API"
            subJob = subJobRepository.save(subJob)
            subJob.additionalData = callApis(
                remoteId = remoteId,
                renderingJob = renderingJob
            )
            subJob.status = SubJobStatus.FINISHED
            subJob.finishedDate = Instant.now()
        } catch (exception: Exception) {
            // The WebClient exception message embeds the request URI, which carries the IIIF consumer key.
            log.error(
                "Error while processing DDB communication: ${LogSanitizer.sanitize(exception.message)}",
                LogSanitizer.sanitize(exception)
            )
            subJob.errorMessage = GENERIC_CONVERSION_ERROR
            subJob.status = SubJobStatus.FAILED
            subJob.finishedDate = Instant.now()
        } finally {
            subJobRepository.save(subJob)
        }
    }

    private fun callApis(remoteId: String, renderingJob: RenderingJob): Map<String, String> {
        val apiToken = getToken(renderingJob)
        val restResponse = callRestApi(remoteId, apiToken)
        val sizes = callIiifApi(restResponse.binaryRef, apiToken)
        val additionalData = mutableMapOf(
            "linkTemplate" to "${ddbApiProperties.iiifApiBaseUrl}/${restResponse.binaryRef}/full/!${DdbRenderModule.Companion.WIDTH_PLACEHOLDER},${DdbRenderModule.Companion.HEIGHT_PLACEHOLDER}/0/default.jpg",
            "widthPlaceHolder" to DdbRenderModule.WIDTH_PLACEHOLDER,
            "heightPlaceHolder" to DdbRenderModule.HEIGHT_PLACEHOLDER,
            "institution" to restResponse.institution,
            "licenseLink" to restResponse.licenseLink,
            "licenseGroup" to restResponse.licenseGroup
        )
        sizes.forEachIndexed { index, size ->
            additionalData.put("size_$index", "${size.first},${size.second}")
        }
        return additionalData
    }

    private fun callRestApi(remoteId: String, apiToken: String): DdbRestData {
        log.debug("Calling DDB REST API for remoteId $remoteId at ${ddbApiProperties.restApiBaseUrl}")
        val webClient = webClientBuilder.clone().baseUrl(ddbApiProperties.restApiBaseUrl).build()

        val response = guard.block(retryable = true) {
            val spec = webClient.get().uri {
                val builder = it.path("/items/${remoteId}")
                if (ddbApiProperties.authMode == DdbAuthMode.QUERY) builder.queryParam("oauth_consumer_key", apiToken)
                builder.build()
            }
            // HEADER mode (opt-in, unverified against the live API, see DdbApiProperties) keeps the key out of
            // the URI and thus out of WebClientException messages and access logs.
            (if (ddbApiProperties.authMode == DdbAuthMode.HEADER)
                spec.header(HttpHeaders.AUTHORIZATION, "OAuth oauth_consumer_key=\"$apiToken\"")
            else spec)
                .retrieve().bodyToMono(String::class.java)
                .timeout(CALL_TIMEOUT)
        }

        response?.let {
            val rootNode = parseJson(it, "REST")
            val ref = rootNode.path("binaries").path("binary").path("@ref").asString("")
            val licenseLink = rootNode.path("binaries").path("binary").path("@kind").asString("")
            val licenseGroup = rootNode.path("binaries").path("binary").path("@license_group").asString("")
            if (ref.isNullOrBlank()) {
                throw Exception("DDB API did not return valid response containing remote ref")
            }
            val institution = rootNode.path("view").path("item").path("institution").path("name").asString("")
            return DdbRestData(
                binaryRef = ref,
                institution = institution,
                licenseGroup = licenseGroup,
                licenseLink = licenseLink
            )
        }
        throw Exception("Missing DDB REST API response")
    }

    private fun callIiifApi(binaryRef: String, apiToken: String): List<Pair<Int, Int>> {
        log.debug("Calling DDB IIIF API for binaryRef $binaryRef at ${ddbApiProperties.iiifApiBaseUrl}")
        val webClient = webClientBuilder.clone().baseUrl(ddbApiProperties.iiifApiBaseUrl).build()
        val response = guard.block(retryable = true) {
            webClient
                .get()
                .uri {
                    it.path("/${binaryRef}/info.json").queryParam("oauth_consumer_key", apiToken).build()
                }.retrieve().bodyToMono(String::class.java)
                .timeout(CALL_TIMEOUT)
        }

        response?.let {
            val rootNode = parseJson(it, "IIIF")
            val sizesNode = rootNode.path("sizes")
            val sizes = sizesNode.mapNotNull { sizeNode ->
                val width = sizeNode.path("width").asInt()
                val height = sizeNode.path("height").asInt()
                if (width > 0 && height > 0) Pair(width, height) else null
            }
            if (sizes.isEmpty()) {
                throw Exception("No sizes returned from DDB IIIF API. Cannot build image source")
            }
            log.debug("DDB IIIF API returned ${sizes.size} size(s) for binaryRef $binaryRef")
            return sizes
        }
        throw Exception("Missing DDB IIIF API response")

    }

    /** Parses a DDB response; an HTML error page or empty body yields a readable message instead of a parser error. */
    private fun parseJson(body: String, apiName: String): JsonNode =
        try {
            objectMapper.readTree(body)
        } catch (exception: Exception) {
            throw IllegalStateException(
                "DDB $apiName API returned a non-JSON response (starts with: " +
                    LogSanitizer.sanitize(body.trim().take(80)) + ")"
            )
        }

    private fun getToken(renderingJob: RenderingJob): String {
        val module = moduleRegistry.getRenderModule<RenderModule>(renderingJob.module)
        if (module !is ThirdPartyModule) {
            throw IllegalArgumentException("Unexpected module type: ${module::class.java}")
        }
        val config = module.getCredentials(renderingJob.repoId)
        return config.getOrDefault("apiToken", "")
    }
}
