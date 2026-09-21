package org.edu_sharing.rendering.modules.moodle

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
import org.springframework.web.reactive.function.client.bodyToMono
import java.time.Duration

@Component
class MoodleRenderModule(
    @param:Value($$"${app.session.moodle.nodePermissionExpirationTime}")
    private val nodePermissionExpirationTime: Long?,
    private val moodleJobService: MoodleJobService,
    private val repositoryRegistrationStorageService: RepositoryRegistrationStorageService,
) : RenderModule, ThirdPartyModule {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val SHOW_PREVIEW_IFRAME = "showPreviewIframe"

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

        val webClient = WebClient
            .builder()
            .baseUrl(credentials.getValue("baseurl"))
            .build()

        val webserviceToken = credentials["token"] ?: getWebserviceToken(webClient, credentials.getValue("user"), credentials.getValue("password"))

        log.debug("Calling Moodle ping endpoint for repoId $repoId at ${credentials.getValue("baseurl")}")
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
            .bodyToMono<Int>()
            .timeout(Duration.ofSeconds(credentials.getValue("timeout").toLong()))
            .block()

        if (testResult == null) {
            log.warn("No test result returned from render Moodle.")
        }
        if (testResult != 1) {
            log.warn("Moodle responded but test was not successful. Result: $testResult")
        }
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

    fun getWebserviceToken(webClient: WebClient, user: String, password: String): String {
        log.debug("Requesting Moodle webservice token for user $user")
        val tokenResponse = webClient.get()
            .uri {
                it.path("/login/token.php")
                    .queryParam("username", user)
                    .queryParam("password", password)
                    .queryParam("service", "es-webservice")
                    .build()
            }
            .retrieve()
            .bodyToMono<MoodleTokenReply>()
            .block()

        log.debug("Moodle token response received, token present: ${tokenResponse?.token != null}")
        return tokenResponse?.token ?: throw Exception("Token could not be retrieved from Moodle.")
    }
}
