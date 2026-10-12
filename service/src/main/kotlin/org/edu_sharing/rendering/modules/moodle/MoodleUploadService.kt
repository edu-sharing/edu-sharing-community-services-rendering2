package org.edu_sharing.rendering.modules.moodle

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.edu_sharing.rendering.core.annotation.ConditionalOnConverter
import org.edu_sharing.rendering.utils.LogSanitizer
import org.edu_sharing.rendering.utils.RemoteResponseParser
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import kotlin.time.Duration.Companion.milliseconds

@Service
@ConditionalOnConverter
class MoodleUploadService(
    @param:Qualifier("longRunningWebClientBuilder")
    private val webClientBuilder: WebClient.Builder
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val objectMapper = JsonMapper.builder().build()

    /** Pause between two restore status polls. Mutable for tests only. */
    internal var pollInterval: Duration = RESTORE_POLL_INTERVAL

    /** Overall budget for a restore to finish. Mutable for tests only. */
    internal var restoreDeadline: Duration = RESTORE_DEADLINE

    companion object {
        private val RESTORE_POLL_INTERVAL: Duration = Duration.ofSeconds(1)
        private val RESTORE_DEADLINE: Duration = Duration.ofMinutes(10)

        /** Consecutive transient poll failures (5xx, reset, timeout) tolerated before the restore is abandoned. */
        private const val MAX_CONSECUTIVE_POLL_FAILURES = 5

        private const val REST_PATH = "/webservice/rest/server.php"
    }

    /**
     * @param moodleJobMessage the Moodle job message
     * @param module the Moodle render module
     * @return the constructed URL
     */
    fun getUrl(
        moodleJobMessage: MoodleJobMessage,
        module: MoodleRenderModule,
        repoId: String,
    ): Pair<String, String> {
        val config = module.getCredentials(repoId)
        val webClient = getWebClient(config)
        val webserviceToken = config["token"] ?: module.getWebserviceToken(webClient, config["user"] ?: "", config["password"] ?: "", moodleTimeout(config))
        val courseId = if (module is ScormRenderModule) {
            uploadScorm(
                moodleJobMessage = moodleJobMessage,
                config = config,
                webClient = webClient,
                webserviceToken = webserviceToken
            )
        } else {
            uploadCourse(
                moodleJobMessage = moodleJobMessage,
                config = config,
                webClient = webClient,
                webserviceToken = webserviceToken,
            )
        }
        // Every getUserToken call consumes a one-shot Moodle login token, so the preview link - the
        // iframe src - is only built when the course is actually embedded (module credential
        // showPreviewIframe). The "go to course" link is always shown and always built.
        val previewUrl = if (module.isPreviewIframeEnabled(config)) {
            buildForwardUrl(
                getUserToken(courseId, webClient, webserviceToken, moodleJobMessage, config),
                config
            )
        } else {
            ""
        }
        val linkUrl = buildForwardUrl(
            getUserToken(courseId, webClient, webserviceToken, moodleJobMessage, config),
            config
        )

        return previewUrl to linkUrl
    }

    private fun buildForwardUrl(userToken: String, config: Map<String, String>): String {
        // publicurl is optional and falls back to baseurl. Blank credentials are already dropped
        // when the registration is stored, so the blank checks here only guard registrations
        // written before that and callers passing credentials in directly - without them a blank
        // base silently yields a relative link that no browser can open.
        val baseUrl = config["publicurl"]?.takeIf { it.isNotBlank() }
            ?: config["baseurl"]?.takeIf { it.isNotBlank() }
            ?: throw MoodleUploadException(
                "Neither publicurl nor baseurl is configured for the MOODLE module",
                "Moodle is not configured correctly."
            )

        return UriComponentsBuilder
            .fromUriString(baseUrl)
            .path("/local/edusharing_webservice/forwardUser.php")
            .queryParam("token", userToken)
            .build(false)
            .toUriString()
    }

    private fun uploadScorm(
        moodleJobMessage: MoodleJobMessage,
        config: Map<String, String>,
        webClient: WebClient,
        webserviceToken: String
    ): Int {
        val response = getMoodleUploadResponse(
            moodleJobMessage = moodleJobMessage,
            config = config,
            webClient = webClient,
            webserviceToken = webserviceToken,
            method = "local_edusharing_scorm_course"
        )
        val courseId = response.path("courseId").takeIf { it.isNumber }?.asInt(0) ?: 0
        if (courseId <= 0) {
            throw IllegalStateException(buildMoodleErrorMessage(response))
        }
        return courseId
    }

    private fun uploadCourse(
        moodleJobMessage: MoodleJobMessage,
        config: Map<String, String>,
        webClient: WebClient,
        webserviceToken: String
    ): Int {
        val response = getMoodleUploadResponse(
            moodleJobMessage = moodleJobMessage,
            config = config,
            webClient = webClient,
            webserviceToken = webserviceToken,
            method = "local_edusharing_restore_course"
        )
        val immediateCourseId = response.path("courseId").takeIf { it.isNumber }?.asInt(0) ?: 0
        if (immediateCourseId > 0) {
            return immediateCourseId
        }
        val restoreId = response.path("restoreId").takeIf { it.isNumber }?.asInt()
            ?: throw IllegalStateException(buildMoodleErrorMessage(response))
        triggerCron(config, webClient)

        val startTime = Instant.now()
        var consecutiveFailures = 0

        return runBlocking {
            while (true) {
                if (Duration.between(startTime, Instant.now()) >= restoreDeadline) {
                    throw IllegalStateException("Timeout waiting for restore status after ${restoreDeadline.toMinutes()} minutes")
                }

                val status = try {
                    getStatus(
                        webClient = webClient,
                        webserviceToken = webserviceToken,
                        restoreId = restoreId,
                        config = config
                    ).also { consecutiveFailures = 0 }
                } catch (e: MoodleTransientException) {
                    // A single 5xx / connection reset / timeout must not abort a restore that is still
                    // running in Moodle; the overall deadline above bounds the total wait. Non-transient
                    // errors (4xx, Moodle error object, unparsable body) propagate immediately.
                    if (++consecutiveFailures >= MAX_CONSECUTIVE_POLL_FAILURES) {
                        throw IllegalStateException(
                            "Restore status poll failed $consecutiveFailures times in a row, last error: ${e.message}"
                        )
                    }
                    log.warn(
                        "Transient error polling restore status (attempt {}/{}): {}",
                        consecutiveFailures, MAX_CONSECUTIVE_POLL_FAILURES, e.message
                    )
                    delay(pollInterval.toMillis().milliseconds)
                    continue
                }

                when (status.status.lowercase()) {
                    "success" -> {
                        if (status.courseId != null) {
                            return@runBlocking status.courseId
                        } else {
                            throw IllegalStateException("Restore completed without course ID")
                        }
                    }

                    "failure" -> {
                        throw MoodleUploadException(
                            LogSanitizer.sanitize(status.internalMessage?.takeIf { it.isNotBlank() } ?: "Unknown exception") ?: "",
                            status.userMessage ?: ""
                        )
                    }

                    else -> {
                        delay(pollInterval.toMillis().milliseconds)
                    }
                }
            }
            @Suppress("UNREACHABLE_CODE")
            throw IllegalStateException("Unreachable")
        }
    }

    private fun getUserToken(
        courseId: Int,
        webClient: WebClient,
        webserviceToken: String,
        message: MoodleJobMessage,
        config: Map<String, String>,
    ): String {
        val rawResponse = webClient.postMoodleForm(
            path = REST_PATH,
            query = mapOf("wsfunction" to "local_edusharing_user", "moodlewsrestformat" to "json"),
            form = mapOf(
                "wstoken" to webserviceToken,
                "courseId" to courseId.toString(),
                "firstName" to message.firstName,
                "lastName" to message.lastName,
                "email" to message.userEmail,
                "userName" to message.userName,
            ),
            timeout = moodleTimeout(config),
            what = "Moodle local_edusharing_user call",
        )
        val response = parseMoodleResponse(rawResponse, "local_edusharing_user")
        val token = response.path("token").takeIf { it.isTextual }?.asText()
        if (token.isNullOrBlank()) {
            throw IllegalStateException(buildMoodleErrorMessage(response))
        }
        return URLEncoder.encode(token, StandardCharsets.UTF_8)
    }

    private fun getWebClient(config: Map<String, String>): WebClient {
        val builder = webClientBuilder
            .clone()
            .baseUrl(config["baseurl"] ?: "")

        config["publicurl"]?.takeIf { it.isNotBlank() }?.let { publicUrl ->
            val hostHeader = buildHostHeader(publicUrl)
            if (hostHeader != null) {
                builder.defaultHeader(HttpHeaders.HOST, hostHeader)
            }
        }

        return builder.build()
    }

    private fun buildHostHeader(url: String): String? {
        return try {
            val uri = URI(url)
            val host = uri.host ?: return null
            if (uri.port != -1) "$host:${uri.port}" else host
        } catch (_: Exception) {
            null
        }
    }

    private fun buildMoodleErrorMessage(response: JsonNode): String {
        val exception = response.path("exception").asText("unknown exception")
        val errorCode = response.path("errorcode").asText("unknown error code")
        val message = response.path("message").asText("no message")
        val debugInfo = response.path("debuginfo").asText("no debug info")

        return LogSanitizer.sanitize(buildString {
            append("Moodle error")
            append(": ")
            append(message)
            append(" [errorcode=")
            append(errorCode)
            append(", exception=")
            append(exception)
            append(", debuginfo=")
            append(debugInfo)
            append("]")
        }) ?: ""
    }

    private fun parseMoodleResponse(rawBody: String?, context: String): JsonNode =
        // Empty bodies, HTML pages and non-JSON answers become a readable message with a short,
        // sanitized excerpt instead of the raw response.
        RemoteResponseParser.parse(rawBody, JsonNode::class.java, objectMapper, "Moodle ($context)")

    private fun getMoodleUploadResponse(
        moodleJobMessage: MoodleJobMessage,
        config: Map<String, String>,
        webClient: WebClient,
        webserviceToken: String,
        method: String
    ): JsonNode {
        val response = webClient.postMoodleForm(
            path = REST_PATH,
            query = mapOf("wsfunction" to method, "moodlewsrestformat" to "json"),
            form = mapOf(
                "wstoken" to webserviceToken,
                "nodeId" to moodleJobMessage.nodeId,
                "category" to (config["categoryid"] ?: "1"),
                "title" to moodleJobMessage.title,
            ),
            timeout = moodleTimeout(config),
            what = "Moodle $method call",
        )
        return parseMoodleResponse(response, "wsfunction=$method")
    }

    private fun getStatus(
        webClient: WebClient,
        webserviceToken: String,
        restoreId: Int,
        config: Map<String, String>
    ): RestoreStatusDto {
        val rawResponse = webClient.postMoodleForm(
            path = REST_PATH,
            query = mapOf("wsfunction" to "local_edusharing_status", "moodlewsrestformat" to "json"),
            form = mapOf("wstoken" to webserviceToken, "restoreId" to restoreId.toString()),
            timeout = moodleTimeout(config),
            what = "Moodle local_edusharing_status call",
        )
        val response = parseMoodleResponse(rawResponse, "local_edusharing_status")
        if (!response.has("status")) {
            throw IllegalStateException(buildMoodleErrorMessage(response))
        }
        return RestoreStatusDto(
            status = response.path("status").asText("Unknown status"),
            internalMessage = response.path("internalMessage").asText(""),
            userMessage = response.path("userMessage").asText(""),
            courseId = response.path("courseId").asInt(0).takeIf { it > 0 }
        )
    }

    /**
     * Fire-and-forget: nudges Moodle's cron so the queued restore runs sooner. The password travels in
     * the POST body. Failures are only logged (sanitized, no URI) - the status poll decides the outcome.
     */
    private fun triggerCron(config: Map<String, String>, webClient: WebClient) {
        webClient.post()
            .uri("/admin/cron.php")
            .body(BodyInserters.fromFormData(LinkedMultiValueMap<String, String>().apply {
                add("password", config["password"] ?: "")
            }))
            .retrieve()
            .bodyToMono<String>()
            .timeout(moodleTimeout(config))
            .subscribe(
                { body -> log.debug("Moodle cron triggered, response length: {}", body.length) },
                { error -> log.warn("Failed to trigger moodle cron: {}", LogSanitizer.describe(error)) }
            )
    }
}
