package org.edu_sharing.rendering.modules.moodle

import org.edu_sharing.rendering.core.annotation.ConditionalOnConverter
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Duration

@Service
@ConditionalOnConverter
class MoodleUploadService() {
    /**
     * Constructs the URL to the moodle course following these steps:
     *
     * 1) Call upload course to get the course id from moodle
     * 2) Get the user token for access to the course from moodle (either from credentials or API call)
     * 3) Build the url to the course using the obtained token
     *
     * @param moodleJobMessage the Moodle job message
     * @param module the Moodle render module
     * @return the constructed URL
     */
    fun getUrl(moodleJobMessage: MoodleJobMessage, module: MoodleRenderModule, repoId: String): Pair<String, String> {
        val config = module.getCredentials(repoId)
        val webClient = getWebClient(config)
        val webserviceToken = config["token"] ?: module.getWebserviceToken(webClient, config["user"] ?: "", config["password"] ?: "")
        val courseId = uploadCourse(
            moodleJobMessage = moodleJobMessage,
            module = module,
            config = config,
            webClient = webClient,
            webserviceToken = webserviceToken
        )
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

    private fun uploadCourse(
        moodleJobMessage: MoodleJobMessage,
        module: MoodleRenderModule,
        config: Map<String, String>,
        webClient: WebClient,
        webserviceToken: String
    ): Int {
        val postParams = LinkedMultiValueMap<String, String>()
        postParams.add("nodeid", "${moodleJobMessage.nodeId}_${moodleJobMessage.hash}")
        postParams.add("category", config["categoryid"] ?: "1")
        postParams.add("title", moodleJobMessage.title)
        val method = module.getRemoteServiceMethod()
        val courseIdRaw = webClient.post()
            .uri {
                it.path("/webservice/rest/server.php")
                    .queryParam("wsfunction", "local_edusharing_$method")
                    .queryParam("moodlewsrestformat", "json")
                    .queryParam("wstoken", webserviceToken)
                    .build()
            }
            .body(BodyInserters.fromFormData(postParams))
            .retrieve()
            .bodyToMono(String::class.java)
            .timeout(Duration.ofSeconds(config["timeout"]?.toLong() ?: 30))
            .block()
        val courseId = courseIdRaw?.toIntOrNull()
        if (courseId === null) {
            throw Exception("Error restoring course to moodle")
        }
        return courseId
    }

    private fun getUserToken(
        courseId: Int,
        webClient: WebClient,
        webserviceToken: String,
        message: MoodleJobMessage
    ): String {
        val postParams = LinkedMultiValueMap<String, String>()
        postParams.add("user_name", message.userName)
        postParams.add("user_givenname", message.firstName)
        postParams.add("user_surname", message.lastName)
        postParams.add("user_email", message.userEmail)
        postParams.add("courseid", courseId.toString())
        postParams.add("role", "student")
        val token = webClient.post()
            .uri {
                it.path("/webservice/rest/server.php")
                    .queryParam("wsfunction", "local_edusharing_handleuser")
                    .queryParam("moodlewsrestformat", "json")
                    .queryParam("wstoken", webserviceToken)
                    .build()
            }
            .body(BodyInserters.fromFormData(postParams))
            .retrieve()
            .bodyToMono(String::class.java)
            .block()
        if (token.isNullOrBlank()) {
            throw Exception("Error getting user token from moodle")
        }
        return URLEncoder.encode(token, StandardCharsets.UTF_8)
    }

    private fun getWebClient(config: Map<String, String>): WebClient {
        val builder = WebClient
            .builder()
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
}
