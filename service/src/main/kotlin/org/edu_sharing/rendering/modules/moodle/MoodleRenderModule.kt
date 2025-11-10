package org.edu_sharing.rendering.modules.moodle

import org.edu_sharing.generated.repository.backend.services.rest.client.model.Node
import org.edu_sharing.rendering.core.dto.ObjectLink
import org.edu_sharing.rendering.core.dto.RenderDataResponse
import org.edu_sharing.rendering.core.dto.RequestUserData
import org.edu_sharing.rendering.edusharingRepo.services.RepositoryRegistrationStorageService
import org.edu_sharing.rendering.modules.RenderModule
import org.edu_sharing.rendering.modules.ThirdPartyModule
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.time.Duration

@Component
class MoodleRenderModule(
    @Value("\${app.session.moodle.nodePermissionExpirationTime}")
    private val nodePermissionExpirationTime: Long?,
    private val moodleJobService: MoodleJobService,
    private val repositoryRegistrationStorageService: RepositoryRegistrationStorageService,
) : RenderModule, ThirdPartyModule {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private val requiredCredentialKeys = setOf("baseurl", "timeout", "categoryid")
        private val optionalCredentialKeys = setOf("user", "password", "token", "submitUserDetails", "publicurl")
    }

    override fun module() = "MOODLE"
    override fun isOptionalModule() = true

    override fun handle(node: Node, userData: RequestUserData): RenderDataResponse {
        return RenderDataResponse(
            module = module(),
            objectLinks = mutableListOf(),
            jobId = moodleJobService.createJob(
                node = node,
                userData = userData,
                module = module(),
                submitUserDetails = getConfig(node.ref.repo).getOrDefault("submitUserDetails", "true").toBoolean()
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

        val webClient = WebClient
            .builder()
            .baseUrl(credentials.getValue("baseurl"))
            .build()

        val webserviceToken = credentials["token"] ?: getWebserviceToken(webClient, credentials.getValue("user"), credentials.getValue("password"))

        val testResult = webClient.get()
            .uri {
                it.path("/webservice/rest/server.php")
                    .queryParam("wsfunction", "local_edusharing_ping")
                    .queryParam("moodlewsrestformat", "json")
                    .queryParam("wstoken", webserviceToken)
                    .queryParam("repoId", repoId)
                    .build()
            }
            .retrieve()
            .bodyToMono(Int::class.java)
            .timeout(Duration.ofSeconds(credentials.getValue("timeout").toLong()))
            .block()

        if (testResult == null) {
            log.warn("No test result returned from render Moodle.")
        }
        if (testResult != 1) {
            log.warn("Moodle responded but test was not successful. Result: $testResult")
        }
    }

    override fun getConfig(repoId: String): Map<String, String> {
        val registration = repositoryRegistrationStorageService.getRegistrationByRepoId(repoId)
            .orElseThrow { IllegalArgumentException("Unknown repository id: $repoId") }
        return registration.module[module()]?.credentials ?: mapOf()
    }

    fun getWebserviceToken(webClient: WebClient, user: String, password: String): String {
        val tokenResponse = webClient.get()
            .uri {
                it.path("/login/token.php")
                    .queryParam("username", user)
                    .queryParam("password", password)
                    .queryParam("service", "es-webservice")
                    .build()
            }
            .retrieve()
            .bodyToMono(MoodleTokenReply::class.java)
            .block()

        return tokenResponse?.token ?: throw Exception("Token could not be retrieved from Moodle.")
    }
}
