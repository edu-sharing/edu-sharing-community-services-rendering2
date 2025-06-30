package org.edu_sharing.rendering.modules.moodle

import io.mockk.mockk
import okhttp3.mockwebserver.MockWebServer

class MoodleUploadServiceTest {

    private val dummyMessage = MoodleJobMessage(
        id = "dummyId",
        nodeId = "dummyNodeId",
        hash = "dummyHash",
        title = "dummyTitle",
        authorityName = "dummyAuthorityName",
        userEmail = "dummyUserEmail",
        userGivenName = "dummyUserGivenName",
        userSurname = "dummyUserSurname"
    )

    private val repoId = "repoId"

    private val mockModule = mockk<MoodleRenderModule>()
    private lateinit var mockWebServer: MockWebServer
    private lateinit var underTest: MoodleUploadService

    /*@BeforeEach
    fun setUp() {
        val config = mapOf<String, String>(
            "baseurl" to mockWebServer.url("/").toString(),
            "user" to "user",
            "password" to "password",
            "timeout" to "90",
            "categoryid" to "1"
        )
        every {mockModule.getConfig(repoId)}returns config
        mockWebServer = MockWebServer()
        mockWebServer.start()
        underTest = MoodleUploadService()
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun testIfGetUrlProperlyCallsPrivateFunctionsAndConstructsCorrectLink() {
        every { mockModule.getRemoteServiceMethod() } returns "restore"

        val expectedUploadCourseBody = "nodeid=dummyNodeId&category=1&title=dummyTitle"
        val expectedGetUserTokenBody =
            "user_name=dummyAuthorityName&user_givenname=dummyUserGivenName&user_surname=dummyUserSurname&user_email=dummyUserEmail&courseid=123&role=student"
        val expectedUploadCourseUrl =
            "http://${mockWebServer.hostName}:${mockWebServer.port}/webservice/rest/server.php?wsfunction=local_edusharing_restore&moodlewsrestformat=json&wstoken=token"
        val expectedGetTokenUrl =
            "http://${mockWebServer.hostName}:${mockWebServer.port}/webservice/rest/server.php?wsfunction=local_edusharing_handleuser&moodlewsrestformat=json&wstoken=token"

        val response1 = MockResponse().setBody("123")
        val response2 = MockResponse().setBody("courseAccessToken")

        mockWebServer.enqueue(response1)
        mockWebServer.enqueue(response2)

        val actualUrl = underTest.getUrl(dummyMessage, mockModule, repoId)

        val request1 = mockWebServer.takeRequest()
        val request2 = mockWebServer.takeRequest()
        val request1Body = request1.body.readUtf8()
        val request2Body = request2.body.readUtf8()
        val request1Url = request1.requestUrl.toString()
        val request2Url = request2.requestUrl.toString()

        Assertions.assertEquals(expectedUploadCourseUrl, request1Url)
        Assertions.assertEquals(expectedGetTokenUrl, request2Url)

        Assertions.assertEquals(expectedUploadCourseBody, request1Body)
        Assertions.assertEquals(expectedGetUserTokenBody, request2Body)

        val expectedUrl = "http://moodlelocal.de/local/edusharing_webservice/forwardUser.php?token=courseAccessToken"
        Assertions.assertEquals(expectedUrl, actualUrl)
    }

    @Test
    fun testIfGetUrlThrowsExceptionIfUploadCourseReturnsNonNumericValue() {
        every { mockModule.getRemoteServiceMethod() } returns "restore"
        val response1 = MockResponse().setBody("abc")
        mockWebServer.enqueue(response1)
        assertThrows<Exception> { underTest.getUrl(dummyMessage, mockModule, repoId) }
    }

    @Test
    fun testIfGetUrlThrowsExceptionIfUploadCourseReturnsEmptyString() {
        every { mockModule.getRemoteServiceMethod() } returns "restore"
        val response1 = MockResponse().setBody("")
        mockWebServer.enqueue(response1)
        assertThrows<Exception> { underTest.getUrl(dummyMessage, mockModule, repoId) }
    }

    @Test
    fun testIfGetUrlThrowsExceptionIfGetUserTokenReturnsEmptyString() {
        every { mockModule.getRemoteServiceMethod() } returns "restore"
        val response1 = MockResponse().setBody("123")
        val response2 = MockResponse().setBody("")
        mockWebServer.enqueue(response1)
        mockWebServer.enqueue(response2)
        assertThrows<Exception> { underTest.getUrl(dummyMessage, mockModule, repoId) }
    }*/
}
