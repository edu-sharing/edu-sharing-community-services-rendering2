package org.edu_sharing.rendering.modules.moodle

import org.edu_sharing.rendering.core.annotation.ConditionalOnConverter
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.util.UriComponentsBuilder
import java.time.Duration

@Service
@ConditionalOnConverter
class MoodleUploadService () {
    /**
     * Constructs the URL to the moodle course following these steps:
     *
     * 1) Call upload course to obtain the course id from moodle
     * 2) Get the user token for access to the course from moodle
     * 3) Build the url to the course using the obtained token
     *
     * @param moodleJobMessage the Moodle job message
     * @param module the Moodle render module
     * @return the constructed URL
     */
    fun getUrl(moodleJobMessage: MoodleJobMessage, module: MoodleRenderModule, repoId: String): String {
        val config = module.getConfig(repoId)
        val webClient = getWebClient(config)
        val webserviceToken = module.getToken(webClient, config["user"] ?: "", config["password"] ?: "")
        val courseId = uploadCourse(
            moodleJobMessage = moodleJobMessage,
            module = module,
            config = config,
            webClient = webClient,
            webserviceToken = webserviceToken
        )
        val userToken = getUserToken(
            moodleJobMessage = moodleJobMessage,
            courseId = courseId,
            config = config,
            webClient = webClient
        )
        return buildForwardUrl(userToken, config)
    }

    private fun buildForwardUrl(userToken: String, config: Map<String, String>): String {
        return UriComponentsBuilder
            .fromHttpUrl(config["baseurl"] ?: "")
            .path("/local/edusharing_webservice/forwardUser.php")
            .queryParam("token", userToken)
            .build()
            .encode()
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
        postParams.add("nodeid", moodleJobMessage.nodeId)
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
        moodleJobMessage: MoodleJobMessage,
        courseId: Int,
        config: Map<String, String>,
        webClient: WebClient
    ): String {
        val postParams = LinkedMultiValueMap<String, String>()
        postParams.add("user_name", moodleJobMessage.authorityName)
        postParams.add("user_givenname", moodleJobMessage.userGivenName)
        postParams.add("user_surname", moodleJobMessage.userSurname)
        postParams.add("user_email", moodleJobMessage.userEmail)
        postParams.add("courseid", courseId.toString())
        postParams.add("role", "student")
        val token = webClient.post()
            .uri {
                it.path("/webservice/rest/server.php")
                    .queryParam("wsfunction", "local_edusharing_handleuser")
                    .queryParam("moodlewsrestformat", "json")
                    .queryParam("wstoken", config["token"] ?: "")
                    .build()
            }
            .body(BodyInserters.fromFormData(postParams))
            .retrieve()
            .bodyToMono(String::class.java)
            .block()
        if (token.isNullOrBlank()) {
            throw Exception("Error getting user token from moodle")
        }
        return token
    }

    private fun getWebClient(config: Map<String, String>): WebClient {
        return WebClient
            .builder()
            .baseUrl(config["baseurl"] ?: "")
            .build()
    }
}