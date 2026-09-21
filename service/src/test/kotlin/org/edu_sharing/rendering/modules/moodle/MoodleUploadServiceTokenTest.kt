package org.edu_sharing.rendering.modules.moodle

import io.mockk.every
import io.mockk.mockk
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.edu_sharing.rendering.edusharingRepo.entity.ModuleSettings
import org.edu_sharing.rendering.edusharingRepo.entity.RepositoryRegistration
import org.edu_sharing.rendering.edusharingRepo.services.RepositoryRegistrationStorageService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import java.util.Optional

/**
 * Every user token is a one-shot Moodle login token, so the preview forward link must only be built
 * when the course is actually embedded. SCORM is used here because it uploads in a single call,
 * which keeps the exchange short - the token logic it inherits is the same for both modules.
 */
class MoodleUploadServiceTokenTest {

    private val repoId = "repo1"

    private val repositoryRegistrationStorageService = mockk<RepositoryRegistrationStorageService>()

    private val message = MoodleJobMessage(
        id = "dummyId",
        nodeId = "dummyNodeId",
        hash = "dummyHash",
        title = "dummyTitle",
        userName = "dummyUserName",
        userEmail = "dummy@example.org",
        firstName = "dummyFirstName",
        lastName = "dummyLastName"
    )

    private lateinit var mockServer: MockWebServer
    private lateinit var registration: RepositoryRegistration
    private lateinit var module: ScormRenderModule
    private lateinit var underTest: MoodleUploadService

    @BeforeEach
    fun setup() {
        mockServer = MockWebServer()
        mockServer.start()

        registration = RepositoryRegistration(repoId = repoId, url = "http://repo.url", publicKey = "key")
        every { repositoryRegistrationStorageService.getRegistrationByRepoId(repoId) } returns Optional.of(registration)

        module = ScormRenderModule(99L, mockk(relaxed = true), repositoryRegistrationStorageService)
        underTest = MoodleUploadService(WebClient.builder())
    }

    @AfterEach
    fun tearDown() = mockServer.shutdown()

    private fun credentials(vararg flags: Pair<String, String>) {
        registration.module["SCORM"] = ModuleSettings(
            credentials = mapOf(
                "baseurl" to mockServer.url("/").toString(),
                "token" to "wstoken",
                "timeout" to "30",
                "categoryid" to "1",
                "password" to "renderer"
            ) + mapOf(*flags)
        )
    }

    private fun json(body: String) = MockResponse()
        .setBody(body)
        .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)

    /** Queues the SCORM upload reply plus [userTokens] one-shot login tokens. */
    private fun enqueueMoodle(userTokens: Int) {
        mockServer.enqueue(json("""{"courseId": 42}"""))
        repeat(userTokens) { mockServer.enqueue(json("""{"token": "usertoken$it"}""")) }
    }

    private fun requestedFunctions() = (1..mockServer.requestCount)
        .map { mockServer.takeRequest().requestUrl!!.queryParameter("wsfunction") }

    @Test
    fun `an embedded course requests one token for the iframe and one for the link`() {
        credentials()
        enqueueMoodle(userTokens = 2)

        val (previewUrl, linkUrl) = underTest.getUrl(message, module, repoId)

        assertEquals(
            listOf("local_edusharing_scorm_course", "local_edusharing_user", "local_edusharing_user"),
            requestedFunctions()
        )
        assertTrue(previewUrl.contains("token=usertoken0"))
        assertTrue(linkUrl.contains("token=usertoken1"))
    }

    @Test
    fun `without the iframe only the link token is requested`() {
        credentials("showPreviewIframe" to "false")
        enqueueMoodle(userTokens = 1)

        val (previewUrl, linkUrl) = underTest.getUrl(message, module, repoId)

        assertEquals(
            listOf("local_edusharing_scorm_course", "local_edusharing_user"),
            requestedFunctions()
        )
        assertEquals("", previewUrl)
        assertTrue(linkUrl.contains("token=usertoken0"))
    }

}
