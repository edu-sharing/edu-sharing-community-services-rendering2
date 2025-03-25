package org.edu_sharing.rendering.modules.h5p

import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verifySequence
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.edu_sharing.rendering.cacheCleaner.TrackingService
import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.edusharingRepo.services.ContentTransferService
import org.edu_sharing.rendering.modules.h5p.lumi.LumiContentManagementService
import org.edu_sharing.rendering.modules.h5p.lumi.dto.LumiNodeInfo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import java.util.*

@ExtendWith(MockKExtension::class)
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
    private val lumiContentManagementService = mockk<LumiContentManagementService>()
    private val trackingService = mockk<TrackingService>()

    @BeforeEach
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        webClient = WebClient.builder()
            .baseUrl(mockWebServer.url("/").toString())
            .build()

        // Initialize your service with mocked dependencies
        underTest =
            H5pUploadService(mockContentTransferService, webClient, lumiContentManagementService, trackingService)
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

        every { lumiContentManagementService.setCache(nodeInfo) } returns nodeInfo
        every { lumiContentManagementService.getContentBucket() } returns "contentBucket123"
        justRun { trackingService.trackCacheObject(dummyCacheObject, "contentBucket123") }

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
        assert(request.path == "/edusharing/nodeid/${dummyCacheObject.nodeId}_${dummyCacheObject.hash}")
        assert(request.method == "GET")
        assert(returnedId == expectedContentId)
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
        every { lumiContentManagementService.setCache(nodeInfo) } returns nodeInfo
        every { lumiContentManagementService.getContentBucket() } returns "contentBucket123"
        justRun { trackingService.trackCacheObject(dummyCacheObject, "contentBucket123") }

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
        assert(body.contains("name=\"file\"; filename=\"${dummyCacheObject.nodeId}_${dummyCacheObject.hash}.h5p\""))
        assert(body.contains(testFileContent))
        assert(body.contains("name=\"nodeId\""))
        assert(body.contains("${dummyCacheObject.nodeId}_${dummyCacheObject.hash}"))
        assert(returnedId == expectedContentId)

        verifySequence {
            lumiContentManagementService.getContentBucket()
            trackingService.trackCacheObject(dummyCacheObject, "contentBucket123")
            mockContentTransferService.getAsInputStream(dummyCacheObject)
            lumiContentManagementService.setCache(nodeInfo)
        }
    }

    @Test
    fun testGetContentIdThrowsExceptionIfGetCachedIdThrowsException() {
        // Arrange
        every { lumiContentManagementService.getContentBucket() } returns "contentBucket123"
        justRun { trackingService.trackCacheObject(dummyCacheObject, "contentBucket123") }
        val mockResponse = MockResponse().setResponseCode(500)
        mockWebServer.enqueue(mockResponse)

        // Act and assert
        assertThrows<Exception> { underTest.getContentId(dummyCacheObject) }

        verifySequence {
            lumiContentManagementService.getContentBucket()
            trackingService.trackCacheObject(dummyCacheObject, "contentBucket123")
        }
    }

    @Test
    fun testGetContentIdThrowsExceptionIfUploadThrowsException() {
        // Arrange
        val testFileContent = UUID.randomUUID().toString()

        every { mockContentTransferService.getAsInputStream(dummyCacheObject) } returns testFileContent.toByteArray()
            .inputStream()
        every { lumiContentManagementService.getContentBucket() } returns "contentBucket123"
        justRun { trackingService.trackCacheObject(dummyCacheObject, "contentBucket123") }

        val getCachedContentIdResponse = MockResponse().setResponseCode(404)
        val uploadContentResponse: MockResponse = MockResponse().setResponseCode(400)

        mockWebServer.enqueue(getCachedContentIdResponse)
        mockWebServer.enqueue(uploadContentResponse)

        // Act and assert
        assertThrows<Exception> { underTest.getContentId(dummyCacheObject) }

        verifySequence {
            lumiContentManagementService.getContentBucket()
            trackingService.trackCacheObject(dummyCacheObject, "contentBucket123")
            mockContentTransferService.getAsInputStream(dummyCacheObject)
        }
    }
}