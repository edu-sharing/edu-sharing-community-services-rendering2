package org.edu_sharing.rendering.modules.moodle

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.edu_sharing.generated.repository.backend.services.rest.client.model.Node
import org.edu_sharing.rendering.core.dto.ObjectLink
import org.edu_sharing.rendering.core.dto.RenderDataResponse
import org.edu_sharing.rendering.edusharingRepo.services.RepositoryRegistrationStorageService
import org.edu_sharing.rendering.modules.RenderModule
import org.edu_sharing.rendering.modules.ThirdPartyModule
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToMono
import java.time.Duration
import java.util.concurrent.TimeoutException

@Component
class MoodleRenderModule(
    @param:Value($$"${app.session.moodle.nodePermissionExpirationTime}")
    private val nodePermissionExpirationTime: Long?,
    private val moodleJobService: MoodleJobService,
    private val repositoryRegistrationStorageService: RepositoryRegistrationStorageService,
    // The central builder from WebClientConfig carries the connect/response timeouts; a bare
    // WebClient.builder() has none and would let a hanging Moodle block the calling thread forever.
    private val webClientBuilder: WebClient.Builder = WebClient.builder(),
) : RenderModule, ThirdPartyModule {

    private val log = LoggerFactory.getLogger(javaClass)
    private val objectMapper = ObjectMapper()

    companion object {
        private const val SHOW_PREVIEW_IFRAME = "showPreviewIframe"

        private val DEFAULT_TOKEN_TIMEOUT: Duration = Duration.ofSeconds(60)

        private val requiredCredentialKeys = setOf("baseurl", "timeout", "categoryid", "password")

        /** Optional *connection* credentials for the Moodle webservice - all secrets or internals. */
        private val optionalCredentialKeys = setOf("user", "token", "submitUserDetails", "publicurl")
    }

    override fun module() = "MOODLE"
    override fun isOptionalModule() = true

    override fun handle(node: Node): RenderDataResponse {
        log.debug("Handling node ${node.ref.id} via module ${module()}, creating async job")
        return RenderDataResponse(
            module = module(),
            objectLinks = mutableListOf(),
            jobId = moodleJobService.createJob(
                node = node,
                module = module(),
            )
        )
    }

    override fun getObjectLinkFromJobData(subJob: SubJob, renderingJob: RenderingJob): ObjectLink? {
        return ObjectLink(link = subJob.message ?: "")
    }

    fun getRemoteServiceMethod() = "restore"

    override fun getNodePermissionExpirationTime() = nodePermissionExpirationTime

    override fun validateThirdPartyCredentials(credentials: Map<String, String>, repoId: String) {
       validateCredentials(
           credentials = credentials,
           requiredCredentialKeys = requiredCredentialKeys.filterNot { optionalCredentialKeys.contains(it) }.toSet(),
           moduleName = module()
       )

        if ((credentials["user"] == null || credentials["password"] == null) && credentials["token"] == null) {
            throw IllegalArgumentException("User and password or token must be provided.")
        }

        val timeout = Duration.ofSeconds(credentials.getValue("timeout").toLong())
        val webClient = webClientBuilder
            .clone()
            .baseUrl(credentials.getValue("baseurl"))
            .build()

        val webserviceToken = credentials["token"] ?: getWebserviceToken(webClient, credentials.getValue("user"), credentials.getValue("password"), timeout)

        log.debug("Calling Moodle ping endpoint for repoId $repoId at ${credentials.getValue("baseurl")}")
        val rawResponse = webClient.get()
            .uri {
                it.path("/webservice/rest/server.php")
                    .queryParam("wsfunction", "local_edusharing_ping")
                    .queryParam("moodlewsrestformat", "json")
                    .queryParam("wstoken", webserviceToken)
                    .queryParam("repoId", repoId)
                    .build()
            }
            .retrieve()
            .bodyToMono<String>()
            .timeout(timeout)
            .onErrorMap { transportError("Moodle ping request", it) }
            .block()

        // Moodle webservice errors (e.g. a revoked token, missing capability) come back as a JSON
        // object instead of the plain integer the endpoint normally returns - parse leniently instead
        // of decoding straight to Int, or such an error crashes credential validation with an opaque
        // Jackson exception instead of a readable one.
        val response = parseMoodlePingResponse(rawResponse)
        if (response.has("exception")) {
            throw IllegalArgumentException(buildMoodleErrorMessage(response))
        }
        val testResult = response.takeIf { it.isInt }?.asInt()

        if (testResult == null) {
            log.warn("No test result returned from render Moodle. Raw response: $rawResponse")
        }
        if (testResult != 1) {
            log.warn("Moodle responded but test was not successful. Result: $testResult")
        }
    }

    private fun parseMoodlePingResponse(rawBody: String?): JsonNode {
        if (rawBody.isNullOrBlank()) {
            throw IllegalArgumentException("Empty response from Moodle ping endpoint.")
        }
        return try {
            objectMapper.readTree(rawBody)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid JSON response from Moodle ping endpoint: $rawBody", e)
        }
    }

    private fun buildMoodleErrorMessage(response: JsonNode): String {
        val exception = response.path("exception").asText("unknown exception")
        val errorCode = response.path("errorcode").asText("unknown error code")
        val message = response.path("message").asText("no message")
        return "Moodle ping failed [errorcode=$errorCode, exception=$exception]: $message"
    }

    override fun getCredentials(repoId: String): Map<String, String> {
        val registration = repositoryRegistrationStorageService.getRegistrationByRepoId(repoId)
            .orElseThrow { IllegalArgumentException("Unknown repository id: $repoId") }
        return registration.module[module()]?.credentials ?: mapOf()
    }

    /**
     * Whether the course is embedded in an iframe. Also decides whether the preview forward link is
     * built at all - see [MoodleUploadService.getUrl]. The "go to course" link is not switchable:
     * without the iframe it is the only way into the course, and with it the only way to open the
     * course full-page.
     */
    fun isPreviewIframeEnabled(credentials: Map<String, String>) = !credentials[SHOW_PREVIEW_IFRAME].isSwitchedOff()

    /**
     * Only the display switch above is ever handed to the browser, as a derived boolean. No
     * credential value is passed through: unlike SODIX, whose two client-visible keys are harmless,
     * this module's optional credentials are the Moodle webservice token, user and publicurl.
     */
    override fun getAdditionalData(subJob: SubJob, repoId: String): Map<String, String> {
        val clientSettings = mapOf(
            SHOW_PREVIEW_IFRAME to isPreviewIframeEnabled(getCredentials(repoId)).toString()
        )
        // Config last: no receiver writes that name, so the merge is disjoint by construction - but
        // should that change, a deployment policy switch must win over per-job data.
        return (subJob.additionalData ?: emptyMap()) + clientSettings
    }

    /**
     * The switch defaults to on - today's behaviour. Unset is indistinguishable from absent
     * (RepositoryRegistrationService drops blank values), so "absent" has to mean "on", and only a
     * literal `false` (any case, surrounding whitespace ignored) turns it off: an unexpected value
     * ("1", "yes", a typo) then fails towards the visible default instead of silently hiding the
     * course.
     */
    private fun String?.isSwitchedOff() = this?.trim().equals("false", ignoreCase = true)

    /**
     * Requests a webservice token via `login/token.php`.
     *
     * Moodle answers with HTTP 200 even on failure: either a JSON error object (`{"error": ..., "errorcode": ...}`)
     * or, for a wrong base URL / maintenance page, HTML. Both are parsed leniently so the Moodle message
     * reaches the caller. Exceptions raised here deliberately carry no cause and no URL: the request URL
     * contains the username and password as query parameters.
     */
    fun getWebserviceToken(
        webClient: WebClient,
        user: String,
        password: String,
        timeout: Duration = DEFAULT_TOKEN_TIMEOUT,
    ): String {
        log.debug("Requesting Moodle webservice token for user $user")
        val rawResponse = webClient.get()
            .uri {
                it.path("/login/token.php")
                    .queryParam("username", user)
                    .queryParam("password", password)
                    .queryParam("service", "es-webservice")
                    .build()
            }
            .retrieve()
            .bodyToMono<String>()
            .timeout(timeout)
            .onErrorMap { transportError("Moodle token request", it) }
            .block()

        val token = parseTokenResponse(rawResponse)
        log.debug("Moodle token response received, token present: true")
        return token
    }

    private fun parseTokenResponse(rawBody: String?): String {
        if (rawBody.isNullOrBlank()) {
            throw IllegalStateException("Token could not be retrieved from Moodle: empty response.")
        }
        val node = try {
            objectMapper.readTree(rawBody)
        } catch (_: Exception) {
            // HTML or plain text - never echo the body, it may be a large page.
            throw IllegalStateException("Token could not be retrieved from Moodle: unexpected non-JSON response (is the base URL correct?).")
        }
        val token = node?.path("token")?.takeIf { it.isTextual }?.asText()
        if (!token.isNullOrBlank()) {
            return token
        }
        val error = node?.path("error")?.asText("")?.takeIf { it.isNotBlank() }
        val errorCode = node?.path("errorcode")?.asText("")?.takeIf { it.isNotBlank() }
        if (error != null || errorCode != null) {
            throw IllegalStateException(
                "Token could not be retrieved from Moodle: ${error ?: "unknown error"}" +
                    (errorCode?.let { " [errorcode=$it]" } ?: "")
            )
        }
        throw IllegalStateException("Token could not be retrieved from Moodle: response contains no token.")
    }

    /**
     * Replaces transport/HTTP exceptions with a readable one. The originals embed the full request URI
     * (including query parameters such as password / wstoken) in their message, so they are neither
     * kept as cause nor printed.
     */
    private fun transportError(what: String, e: Throwable): Throwable = when (e) {
        is WebClientResponseException -> IllegalStateException("$what failed: Moodle answered with HTTP ${e.statusCode.value()}.")
        is TimeoutException -> IllegalStateException("$what timed out.")
        is WebClientRequestException -> IllegalStateException("$what failed: Moodle not reachable (${e.cause?.javaClass?.simpleName ?: "I/O error"}).")
        is IllegalArgumentException, is IllegalStateException -> e
        else -> IllegalStateException("$what failed (${e.javaClass.simpleName}).")
    }
}
