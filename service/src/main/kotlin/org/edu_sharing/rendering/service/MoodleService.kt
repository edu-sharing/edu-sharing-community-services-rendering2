package org.edu_sharing.rendering.service

import org.edu_sharing.rendering.config.annotation.ConditionalOnMoodle
import org.edu_sharing.rendering.dto.RenderDataRequest
import org.edu_sharing.rendering.dto.RenderModules
import org.edu_sharing.rendering.dto.mapper.Mapper
import org.edu_sharing.rendering.dto.queue.MoodleJobMessage
import org.edu_sharing.rendering.repository.mongo.RenderingJobRepository
import org.slf4j.LoggerFactory
import org.springframework.amqp.core.AmqpTemplate
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.util.UriComponentsBuilder

@Service
@ConditionalOnMoodle
class MoodleService (
    private val moodleWebClient: WebClient,
    private val mapper: Mapper,
    private val jobRepository: RenderingJobRepository,
    private val amqpTemplate: AmqpTemplate,
    ) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Value("\${app.moodle.host}")
    lateinit var moodleBaseUrl: String

    @Value("\${app.moodle.token}")
    lateinit var token: String

    @Value("\${app.moodle.categoryid}")
    lateinit var categoryId: String

    @Value("\${edu_sharing.queue.topicExchange}")
    lateinit var topicExchangeName: String

    @Value("\${edu_sharing.queue.moodle.key}")
    lateinit var jobRoutingKey: String

    fun getUrl(moodleJobMessage: MoodleJobMessage, module: RenderModules): String {
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

    private fun uploadCourse(moodleJobMessage: MoodleJobMessage, module: RenderModules): Int {
        val postParams = LinkedMultiValueMap<String, String>();
        postParams.add("nodeid", moodleJobMessage.nodeId)
        postParams.add("category", categoryId)
        postParams.add("title", moodleJobMessage.title)
        val method = if (module == RenderModules.MOODLE) "restore" else "scorm"
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
        val postParams = LinkedMultiValueMap<String, String>();
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

    fun createJob(request: RenderDataRequest): String? {
        try {
            checkPrerequisites()
        } catch (exception: IllegalStateException) {
            log.warn("Could not create Moodle course creation job: " + exception.message + ". Using default module.")
            return null
        }
        if (request.userData == null) {
            log.error("Missing user data in Moodle request. Node: " + request.nodeId)
            throw IllegalArgumentException()
        }
        val job = mapper.cacheObjectToRenderingJob(mapper.renderDataRequestToCacheObject(request))
        job.module = RenderModules.MOODLE
        jobRepository.save(job)
        val message = MoodleJobMessage(
            id = job.id.toString(),
            nodeId = job.esObjectId,
            title = request.title ?: "",
            authorityName = request.userData.authorityName,
            userEmail = request.userData.userEMail,
            userGivenName = request.userData.firstName,
            userSurname = request.userData.surName
        )
        amqpTemplate.convertAndSend(topicExchangeName, jobRoutingKey, message)
        return job.id.toString()
    }

    private fun checkPrerequisites() {
        if (token.isBlank() || categoryId.isBlank()) {
            throw IllegalStateException("Moodle config invalid and/or incomplete")
        }
    }
}