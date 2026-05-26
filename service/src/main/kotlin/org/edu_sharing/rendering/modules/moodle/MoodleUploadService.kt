package org.edu_sharing.rendering.modules.moodle

import com.fasterxml.jackson.databind.JsonNode
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.edu_sharing.rendering.core.annotation.ConditionalOnConverter
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import org.springframework.web.util.UriComponentsBuilder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import kotlin.time.Duration.Companion.milliseconds

@Service
@ConditionalOnConverter
class MoodleUploadService() {
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
        val webserviceToken = config["token"] ?: module.getWebserviceToken(webClient, config["user"] ?: "", config["password"] ?: "")
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
        val userTokenPreview = getUserToken(
            courseId = courseId,
            webClient = webClient,
            webserviceToken = webserviceToken,
            message = moodleJobMessage
        )
        val userTokenLink = getUserToken(
            courseId = courseId,
            webClient = webClient,
            webserviceToken = webserviceToken,
            message = moodleJobMessage
        )

        val previewUrl = buildForwardUrl(userTokenPreview, config)
        val linkUrl = buildForwardUrl(userTokenLink, config)

        return previewUrl to linkUrl
    }

    private fun buildForwardUrl(userToken: String, config: Map<String, String>): String {
        return UriComponentsBuilder
            .fromUriString(config["publicurl"] ?: config["baseurl"] ?: "")
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
        if (!response.has("courseId")) {
            throw Exception(buildMoodleErrorMessage(response))
        }
        return response.get("courseId").asInt()
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
        if (!response.has("courseId") && !response.has("restoreId")) {
            throw Exception(buildMoodleErrorMessage(response))
        }
        if (response.has("courseId")) {
            return response.get("courseId").asInt()
        }
        triggerCron(config, webClient)
        val restoreId = response.get("restoreId").asInt()

        val startTime = Instant.now()
        val timeoutDuration = Duration.ofMinutes(10)

        return runBlocking {
            while (true) {
                val elapsedTime = Duration.between(startTime, Instant.now())
                if (elapsedTime >= timeoutDuration) {
                    throw Exception("Timeout waiting for restore status after 10 minutes")
                }

                val status = getStatus(
                    webClient = webClient,
                    webserviceToken = webserviceToken,
                    restoreId = restoreId,
                    config = config
                )

                when (status.status.lowercase()) {
                    "success" -> {
                        if (status.courseId != null) {
                            return@runBlocking status.courseId
                        } else {
                            throw Exception("Restore completed without course ID")
                        }
                    }

                    "failure" -> {
                        throw MoodleUploadException(status.internalMessage ?: "Unknown exception", status.userMessage ?: "")
                    }

                    else -> {
                        delay(1000.milliseconds)
                    }
                }
            }
            throw IllegalStateException("Unreachable")
        }
    }

    private fun getUserToken(
        courseId: Int,
        webClient: WebClient,
        webserviceToken: String,
        message: MoodleJobMessage
    ): String {
        val response = webClient.get()
            .uri {
                it.path("/webservice/rest/server.php")
                    .queryParam("wsfunction", "local_edusharing_user")
                    .queryParam("moodlewsrestformat", "json")
                    .queryParam("wstoken", webserviceToken)
                    .queryParam("courseId", courseId.toString())
                    .queryParam("firstName", message.firstName)
                    .queryParam("lastName", message.lastName)
                    .queryParam("email", message.userEmail)
                    .queryParam("userName", message.userName)
                    .build()
            }
            .retrieve()
            .bodyToMono<JsonNode>()
            .block()
        if (response == null) {
            throw Exception("Error getting user token. Response is null")
        }
        if (!response.has("token")) {
            throw Exception(buildMoodleErrorMessage(response))
        }
        return response.get("token").asText().let {
            URLEncoder.encode(it, StandardCharsets.UTF_8)
        }
    }

    private fun getWebClient(config: Map<String, String>): WebClient {
        return WebClient
            .builder()
            .baseUrl(config["baseurl"] ?: "")
            .build()
    }

    private fun buildMoodleErrorMessage(response: JsonNode): String {
        val exception = response.path("exception").asText("unknown exception")
        val errorCode = response.path("errorcode").asText("unknown error code")
        val message = response.path("message").asText("no message")
        val debugInfo = response.path("debuginfo").asText("no debug info")

        return buildString {
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
        }
    }

    private fun getMoodleUploadResponse(
        moodleJobMessage: MoodleJobMessage,
        config: Map<String, String>,
        webClient: WebClient,
        webserviceToken: String,
        method: String
    ): JsonNode {
        val response = webClient.get()
            .uri {
                it.path("/webservice/rest/server.php")
                    .queryParam("wsfunction", method)
                    .queryParam("moodlewsrestformat", "json")
                    .queryParam("wstoken", webserviceToken)
                    .queryParam("nodeId", "${moodleJobMessage.nodeId}_${moodleJobMessage.hash}")
                    .queryParam("category", config["categoryid"] ?: "1")
                    .queryParam("title", moodleJobMessage.title)
                    .build()
            }
            .retrieve()
            .bodyToMono<JsonNode>()
            .timeout(Duration.ofSeconds(config["timeout"]?.toLong() ?: 30))
            .block()
        if (response == null) {
            throw Exception("Error uploading course to moodle")
        }
        return response
    }

    private fun getStatus(
        webClient: WebClient,
        webserviceToken: String,
        restoreId: Int,
        config: Map<String, String>
    ): RestoreStatusDto {
        val response = webClient.get()
            .uri {
                it.path("/webservice/rest/server.php")
                    .queryParam("wsfunction", "local_edusharing_status")
                    .queryParam("moodlewsrestformat", "json")
                    .queryParam("wstoken", webserviceToken)
                    .queryParam("restoreId", restoreId)
                    .build()
            }
            .retrieve()
            .bodyToMono<JsonNode>()
            .timeout(Duration.ofSeconds(config["timeout"]?.toLong() ?: 30))
            .block()
        if (response == null) {
            throw Exception("Error getting status from moodle")
        }
        if (!response.has("status")) {
            throw Exception(buildMoodleErrorMessage(response))
        }
        return RestoreStatusDto(
            status = response.get("status").asText("Unknown status"),
            internalMessage = response.get("internalMessage").asText(""),
            userMessage = response.get("userMessage").asText(""),
            courseId = response.get("courseId").asInt(0)
        )
    }

    private fun triggerCron(config: Map<String, String>, webClient: WebClient) {
        webClient.get()
            .uri {
                it.path("/admin/cron.php?password=${config["password"]}")
                    .build()
            }
            .retrieve()
            .bodyToMono<String>()
            .subscribe()
    }
}
