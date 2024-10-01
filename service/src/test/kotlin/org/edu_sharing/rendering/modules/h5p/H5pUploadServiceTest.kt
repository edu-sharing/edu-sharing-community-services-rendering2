package org.edu_sharing.rendering.modules.h5p

import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.edusharingRepo.services.ContentTransferService
import org.edu_sharing.rendering.modules.h5p.lumi.LumiNodeInfoService
import org.edu_sharing.rendering.modules.h5p.lumi.dto.LumiNodeInfo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import java.util.UUID

class H5pUploadServiceTest {

    private lateinit var underTest: H5pUploadService
    private lateinit var mockWebServer: MockWebServer
    private lateinit var webClient: WebClient

    private val dummyCacheObject = CacheObject(
        nodeId = "testNodeId",
        hash = "testHash",
        type = "h5p",
        repoId = "repo123"
    )

    // Mock the dependencies
    private val mockContentTransferService = mockk<ContentTransferService>()
    private val mockLumiNodeInfoService = mockk<LumiNodeInfoService>()

    @BeforeEach
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        webClient = WebClient.builder()
            .baseUrl(mockWebServer.url("/").toString())
            .build()

        // Initialize your service with mocked dependencies
        underTest = H5pUploadService(mockContentTransferService, webClient, mockLumiNodeInfoService)
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun testGetContentIdCallsLumiServerWithProperRequestAndReturnsContentIdIfAlreadyCached() {
        // Arrange
        val nodeInfo = LumiNodeInfo(
            lumiId = "expectedContentId",
            nodeId = dummyCacheObject.nodeId,
            hash = dummyCacheObject.hash
        )

        every { mockLumiNodeInfoService.setCache(nodeInfo) } returns nodeInfo

        val expectedContentId = "expectedContentId"
        val mockResponse = MockResponse()
            .addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .setBody("""{"contentId": "$expectedContentId"}""")
            .setResponseCode(200)
        mockWebServer.enqueue(mockResponse)

        // Act
        val returnedId = underTest.getContentId(dummyCacheObject)

        // Assert
        val request = mockWebServer.takeRequest()
        assertThat(request.path).isEqualTo("/edusharing/nodeid/${dummyCacheObject.nodeId}_${dummyCacheObject.hash}")
        assertThat(request.method).isEqualTo("GET")

        assertThat(returnedId).isEqualTo(expectedContentId)
    }

    @Test
    fun testGetContentIdCallsLumiServerWithProperRequestsAndReturnsContentIdIfUploadSuccessful() {
        // Arrange
        val nodeInfo = LumiNodeInfo(
            lumiId = "expectedContentId",
            nodeId = dummyCacheObject.nodeId,
            hash = dummyCacheObject.hash
        )

        val testFileContent = UUID.randomUUID().toString()

        every { mockContentTransferService.getAsInputStream(dummyCacheObject) } returns testFileContent.toByteArray()
            .inputStream()
        every { mockLumiNodeInfoService.setCache(nodeInfo) } returns nodeInfo

        val expectedContentId = "expectedContentId"
        val getCachedContentIdResponse = MockResponse().setResponseCode(404)
        val uploadContentResponse: MockResponse = MockResponse()
            .addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .setBody("""{"contentId": "$expectedContentId"}""")
            .setResponseCode(200)

        mockWebServer.enqueue(getCachedContentIdResponse)
        mockWebServer.enqueue(uploadContentResponse)

        // Act
        val returnedId = underTest.getContentId(dummyCacheObject)

        // Assert

        // First request is already tested in other case, we just take it from the internal queue
        mockWebServer.takeRequest()
        val request = mockWebServer.takeRequest()
        val body = request.body.readUtf8()
        assertThat(body).contains("name=\"file\"; filename=\"${dummyCacheObject.nodeId}_${dummyCacheObject.hash}.h5p\"")
        assertThat(body).contains(testFileContent)
        assertThat(body).contains("name=\"nodeId\"")
        assertThat(body).contains("${dummyCacheObject.nodeId}_${dummyCacheObject.hash}")
        assertThat(returnedId).isEqualTo(expectedContentId)

        verify(exactly = 1) { mockContentTransferService.getAsInputStream(dummyCacheObject) }
        verify(exactly = 1) { mockLumiNodeInfoService.setCache(nodeInfo) }
        confirmVerified(mockLumiNodeInfoService, mockContentTransferService)
    }

    @Test
    fun testGetContentIdThrowsExceptionIfGetCachedIdThrowsException() {
        // Arrange
        val mockResponse = MockResponse().setResponseCode(500)
        mockWebServer.enqueue(mockResponse)

        // Act and assert
        assertThrows<Exception> { underTest.getContentId(dummyCacheObject) }
    }

    @Test
    fun testGetContentIdThrowsExceptionIfUploadThrowsException() {
        // Arrange
        val testFileContent = UUID.randomUUID().toString()

        every { mockContentTransferService.getAsInputStream(dummyCacheObject) } returns testFileContent.toByteArray()
            .inputStream()

        val getCachedContentIdResponse = MockResponse().setResponseCode(404)
        val uploadContentResponse: MockResponse = MockResponse().setResponseCode(400)

        mockWebServer.enqueue(getCachedContentIdResponse)
        mockWebServer.enqueue(uploadContentResponse)

        // Act and assert
        assertThrows<Exception> { underTest.getContentId(dummyCacheObject) }
    }
}