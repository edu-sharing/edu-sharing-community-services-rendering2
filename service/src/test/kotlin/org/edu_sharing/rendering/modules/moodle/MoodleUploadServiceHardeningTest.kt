package org.edu_sharing.rendering.modules.moodle

import io.mockk.every
import io.mockk.mockk
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.edu_sharing.rendering.edusharingRepo.entity.ModuleSettings
import org.edu_sharing.rendering.edusharingRepo.entity.RepositoryRegistration
import org.edu_sharing.rendering.edusharingRepo.services.RepositoryRegistrationStorageService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.web.reactive.function.client.WebClient
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.Optional
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * Secrets must travel in the POST body only, Moodle failures must be readable and credential-free,
 * and the restore poll must survive transient errors without hanging on permanent ones.
 */
class MoodleUploadServiceHardeningTest {

    private val repoId = "repo1"
    private val trickyPassword = "p&w=d %+ü#/?"
    private val trickyToken = "tok&en=1 %2B+ä"

    private val message = MoodleJobMessage(
        id = "id", nodeId = "node", hash = "h", title = "A title & more",
        userName = "u", userEmail = "u@example.org", firstName = "F", lastName = "L"
    )

    private lateinit var server: MockWebServer
    private lateinit var registration: RepositoryRegistration
    private lateinit var moodle: MoodleRenderModule
    private lateinit var underTest: MoodleUploadService
    private val requests = CopyOnWriteArrayList<RecordedRequest>()

    @BeforeEach
    fun setup() {
        server = MockWebServer()
        server.start()
        val storage = mockk<RepositoryRegistrationStorageService>()
        registration = RepositoryRegistration(repoId = repoId, url = "http://repo.url", publicKey = "key")
        every { storage.getRegistrationByRepoId(repoId) } returns Optional.of(registration)
        moodle = MoodleRenderModule(1L, mockk(relaxed = true), storage)
        underTest = MoodleUploadService(WebClient.builder())
        underTest.pollInterval = Duration.ofMillis(10)
    }

    @AfterEach
    fun tearDown() = server.shutdown()

    private fun credentials(vararg extra: Pair<String, String>) {
        registration.module["MOODLE"] = ModuleSettings(
            credentials = mapOf(
                "baseurl" to server.url("/").toString(),
                "token" to trickyToken,
                "timeout" to "30",
                "categoryid" to "1",
                "password" to trickyPassword,
                "showPreviewIframe" to "false",
            ) + mapOf(*extra)
        )
    }

    private fun json(body: String) = MockResponse().setBody(body).setHeader("Content-Type", "application/json")

    private fun RecordedRequest.form(): Map<String, String> = body.readUtf8().split("&").filter { it.isNotEmpty() }.associate {
        val (k, v) = it.split("=", limit = 2)
        URLDecoder.decode(k, StandardCharsets.UTF_8) to URLDecoder.decode(v, StandardCharsets.UTF_8)
    }

    private fun RecordedRequest.wsfunction() = requestUrl?.queryParameter("wsfunction")

    /** Dispatches by path / wsfunction; [status] answers the poll calls in order, the last answer repeats. */
    private fun dispatch(status: List<() -> MockResponse>, restore: String = """{"restoreId": 7}""") {
        val polls = AtomicInteger()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                return when {
                    request.path!!.startsWith("/admin/cron.php") -> MockResponse().setBody("cron ok")
                    request.wsfunction() == "local_edusharing_restore_course" -> json(restore)
                    request.wsfunction() == "local_edusharing_status" ->
                        status[minOf(polls.getAndIncrement(), status.size - 1)]()
                    request.wsfunction() == "local_edusharing_user" -> json("""{"token": "ut"}""")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
    }

    private fun statusCalls() = requests.count { it.wsfunction() == "local_edusharing_status" }

    private val success = { json("""{"status":"success","courseId":42}""") }
    private val running = { json("""{"status":"running"}""") }
    private val serverError = { MockResponse().setResponseCode(502) }

    @Test
    fun `secrets are in the body and never in the query, also with special characters`() {
        credentials()
        dispatch(listOf(success))

        underTest.getUrl(message, moodle, repoId)
        Thread.sleep(200) // the fire-and-forget cron call is asynchronous

        assertTrue(requests.any { it.path!!.startsWith("/admin/cron.php") })
        requests.forEach { request ->
            assertEquals("POST", request.method)
            assertEquals("application/x-www-form-urlencoded", request.getHeader("Content-Type")!!.substringBefore(";"))
            assertFalse(request.path!!.contains("wstoken"), request.path)
            assertFalse(request.path!!.contains("password"), request.path)
            assertFalse(request.path!!.contains("tok"), request.path)
        }
        val restore = requests.first { it.wsfunction() == "local_edusharing_restore_course" }.form()
        assertEquals(trickyToken, restore["wstoken"])
        assertEquals("A title & more", restore["title"])
        val cron = requests.first { it.path!!.startsWith("/admin/cron.php") }.form()
        assertEquals(trickyPassword, cron["password"])
        val user = requests.first { it.wsfunction() == "local_edusharing_user" }.form()
        assertEquals(trickyToken, user["wstoken"])
        assertEquals("u@example.org", user["email"])
    }

    @Test
    fun `username and password for token php are sent as encoded form body`() {
        credentials("token" to "", "user" to "us&er")
        val config = moodle.getCredentials(repoId).filterKeys { it != "token" }
        val client = WebClient.builder().baseUrl(server.url("/").toString().trimEnd('/')).build()
        server.enqueue(json("""{"token":"abc"}"""))

        assertEquals("abc", moodle.getWebserviceToken(client, "us&er", config.getValue("password")))

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/login/token.php", request.path)
        val form = request.form()
        assertEquals("us&er", form["username"])
        assertEquals(trickyPassword, form["password"])
        assertEquals("es-webservice", form["service"])
    }

    @Test
    fun `a single transient poll failure does not abort the restore`() {
        credentials()
        dispatch(listOf(running, serverError, running, success))

        val (_, link) = underTest.getUrl(message, moodle, repoId)

        assertTrue(link.contains("token=ut"))
        assertEquals(4, statusCalls())
    }

    @Test
    fun `a poll timeout is tolerated too`() {
        credentials("timeout" to "1")
        dispatch(listOf({ json("""{"status":"running"}""").setBodyDelay(3, java.util.concurrent.TimeUnit.SECONDS) }, success))

        underTest.getUrl(message, moodle, repoId)

        assertEquals(2, statusCalls())
    }

    @Test
    fun `too many consecutive transient failures abort the restore`() {
        credentials()
        dispatch(listOf(serverError))

        val e = assertThrows<IllegalStateException> { underTest.getUrl(message, moodle, repoId) }

        assertTrue(e.message!!.contains("in a row"), e.message)
        assertEquals(5, statusCalls())
    }

    @Test
    fun `a 4xx poll answer aborts immediately`() {
        credentials()
        dispatch(listOf({ MockResponse().setResponseCode(403) }, success))

        val e = assertThrows<IllegalStateException> { underTest.getUrl(message, moodle, repoId) }

        assertTrue(e.message!!.contains("403"))
        assertEquals(1, statusCalls())
    }

    @Test
    fun `a Moodle error object on the poll aborts immediately and is sanitized`() {
        credentials()
        dispatch(listOf({
            json("""{"exception":"moodle_exception","errorcode":"invalidtoken","message":"Invalid token","debuginfo":"wstoken=LEAKED&x=1"}""")
        }, success))

        val e = assertThrows<IllegalStateException> { underTest.getUrl(message, moodle, repoId) }

        assertTrue(e.message!!.contains("invalidtoken"))
        assertFalse(e.message!!.contains("LEAKED"))
        assertEquals(1, statusCalls())
    }

    @Test
    fun `the restore deadline bounds the polling`() {
        credentials()
        underTest.restoreDeadline = Duration.ofMillis(300)
        dispatch(listOf(running))

        val e = assertThrows<IllegalStateException> { underTest.getUrl(message, moodle, repoId) }

        assertTrue(e.message!!.contains("Timeout waiting for restore"))
    }

    @Test
    fun `a failed restore reports a sanitized internal message and the public message`() {
        credentials()
        dispatch(listOf({ json("""{"status":"failure","internalMessage":"boom password=hunter2","userMessage":"Try later"}""") }))

        val e = assertThrows<MoodleUploadException> { underTest.getUrl(message, moodle, repoId) }

        assertFalse(e.message!!.contains("hunter2"))
        assertEquals("Try later", e.publicMessage)
    }

    @Test
    fun `an empty, html or incomplete answer is reported readably`() {
        credentials()
        dispatch(listOf(success), restore = "")
        assertTrue(assertThrows<IllegalStateException> { underTest.getUrl(message, moodle, repoId) }.message!!.contains("empty response"))

        dispatch(listOf(success), restore = "<html>Maintenance wstoken=LEAKED</html>")
        val html = assertThrows<IllegalStateException> { underTest.getUrl(message, moodle, repoId) }
        assertTrue(html.message!!.contains("Maintenance"))
        assertFalse(html.message!!.contains("LEAKED"))

        dispatch(listOf(success), restore = """{"exception":"x","errorcode":"nocap","message":"No capability"}""")
        assertTrue(assertThrows<IllegalStateException> { underTest.getUrl(message, moodle, repoId) }.message!!.contains("nocap"))
    }

    @Test
    fun `a successful poll without course id is not treated as course 0`() {
        credentials()
        dispatch(listOf({ json("""{"status":"success"}""") }))

        val e = assertThrows<IllegalStateException> { underTest.getUrl(message, moodle, repoId) }

        assertTrue(e.message!!.contains("without course ID"))
    }

    @Test
    fun `a hanging Moodle call times out instead of blocking`() {
        credentials("timeout" to "1")
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) =
                json("""{"restoreId": 7}""").setBodyDelay(5, java.util.concurrent.TimeUnit.SECONDS)
        }

        val start = System.nanoTime()
        val e = assertThrows<IllegalStateException> { underTest.getUrl(message, moodle, repoId) }

        assertTrue(Duration.ofNanos(System.nanoTime() - start).seconds < 4)
        assertTrue(e.message!!.contains("timed out"))
    }
}
