package org.edu_sharing.rendering.modules.moodle

import org.edu_sharing.rendering.config.annotation.ConditionalOnMoodle
import org.edu_sharing.rendering.dto.queue.MoodleJobMessage
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.util.UriComponentsBuilder

@Service
@ConditionalOnMoodle
class MoodleUploadService (
    private val moodleWebClient: WebClient
) {

    @Value("\${app.moodle.host}")
    lateinit var moodleBaseUrl: String

    @Value("\${app.moodle.token}")
    lateinit var token: String

    @Value("\${app.moodle.categoryid}")
    lateinit var categoryId: String

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
    fun getUrl(moodleJobMessage: MoodleJobMessage, module: MoodleRenderModule): String {
        val courseId = uploadCourse(moodleJobMessage, module)
        val token = getUserToken(moodleJobMessage, courseId)
        return buildForwardUrl(token)
    }

    private fun buildForwardUrl(token: String): String {
        return UriComponentsBuilder
            .fromHttpUrl(moodleBaseUrl)
            .path("/local/edusharing_webservice/forwardUser.php")
            .queryParam("token", token)
            .build()
            .encode()
            .toUriString()
    }

    private fun uploadCourse(moodleJobMessage: MoodleJobMessage, module: MoodleRenderModule): Int {
        val postParams = LinkedMultiValueMap<String, String>()
        postParams.add("nodeid", moodleJobMessage.nodeId)
        postParams.add("category", categoryId)
        postParams.add("title", moodleJobMessage.title)
        val method = module.getRemoteServiceMethod()
        val courseIdRaw = moodleWebClient.post()
            .uri {
                it.path("/webservice/rest/server.php")
                    .queryParam("wsfunction", "local_edusharing_$method")
                    .queryParam("moodlewsrestformat", "json")
                    .queryParam("wstoken", token)
                    .build()
            }
            .body(BodyInserters.fromFormData(postParams))
            .retrieve()
            .bodyToMono(String::class.java)
            .block()
        val courseId = courseIdRaw?.toIntOrNull()
        if (courseId === null) {
            throw Exception("Error restoring course to moodle")
        }
        return courseId
    }

    private fun getUserToken(moodleJobMessage: MoodleJobMessage, courseId: Int): String {
        val postParams = LinkedMultiValueMap<String, String>()
        postParams.add("user_name", moodleJobMessage.authorityName)
        postParams.add("user_givenname", moodleJobMessage.userGivenName)
        postParams.add("user_surname", moodleJobMessage.userSurname)
        postParams.add("user_email", moodleJobMessage.userEmail)
        postParams.add("courseid", courseId.toString())
        postParams.add("role", "student")
        val token = moodleWebClient.post()
            .uri {
                it.path("/webservice/rest/server.php")
                    .queryParam("wsfunction", "local_edusharing_handleuser")
                    .queryParam("moodlewsrestformat", "json")
                    .queryParam("wstoken", token)
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
}