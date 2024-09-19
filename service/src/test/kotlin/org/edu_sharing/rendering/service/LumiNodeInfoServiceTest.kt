package org.edu_sharing.rendering.service

import io.mockk.*
import io.mockk.junit5.MockKExtension
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.edu_sharing.rendering.modules.h5p.lumi.LumiNodeInfoService
import org.edu_sharing.rendering.processing.h5p.LumiCacheRepository
import org.edu_sharing.rendering.processing.h5p.LumiNodeInfo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.web.reactive.function.client.WebClient
import java.util.*

@ExtendWith(MockKExtension::class)
class LumiNodeInfoServiceTest {
    private val cacheRepository = mockk<LumiCacheRepository>()

    private lateinit var mockServer: MockWebServer
    private lateinit var webClient: WebClient
    private lateinit var underTest: LumiNodeInfoService

    @BeforeEach
    fun setup() {
        mockServer = MockWebServer()
        mockServer.start()
        webClient = WebClient.builder()
            .baseUrl(mockServer.url("/").toString())
            .build()
        underTest = LumiNodeInfoService(webClient, cacheRepository)
    }

    @AfterEach
    fun teardown() {
        mockServer.shutdown()
        clearAllMocks()
    }

    @Test
    fun testGetNodeInfoReturnsCachedInfoIfFound() {
        // Arrange
        val contentId = "content123"
        val expected = LumiNodeInfo(
            lumiId = contentId,
            nodeId = "nodeId123",
            hash = "hash123"
        )
        every { cacheRepository.findById(contentId) } returns Optional.of(expected)

        // Act
        val result = underTest.getNodeInfo(contentId)

        // Assert
        assert(result == expected)
        verify(exactly = 1) { cacheRepository.findById(contentId) }
        confirmVerified(cacheRepository)
    }

    @Test
    fun testGetNodeInfoFetchesInfoFromLumiIfNotCached() {
        // Arrange

        val contentId = "content123"
        val serverResponseBody = "{\"nodeId\": \"nodeId124_hash124\"}"
        val response = MockResponse()
            .addHeader("Content-Type", "application/json; charset=utf-8")
            .setBody(serverResponseBody)
            .setResponseCode(200)
        mockServer.enqueue(response)

        val expectedNodeInfo = LumiNodeInfo(
            lumiId = contentId,
            nodeId = "nodeId124",
            hash = "hash124"
        )

        every { cacheRepository.findById(contentId) } returns Optional.empty()
        every { cacheRepository.save(expectedNodeInfo) } returns expectedNodeInfo

        // Act
        val result = underTest.getNodeInfo(contentId)

        // Assert
        val request = mockServer.takeRequest()

        assert(request.path == "/edusharing/contentid/$contentId")
        assert(request.method == "GET")
        assert(result == expectedNodeInfo)

        verifySequence {
            cacheRepository.findById(contentId)
            cacheRepository.save(expectedNodeInfo)
        }
    }

    @Test
    fun testGetContentIdReturnsCachedIdIfFound() {

        // Arrange
        val nodeId = "node125"
        val hash = "hash125"
        val contentId = "content125"

        val nodeInfo = LumiNodeInfo(
            lumiId = contentId,
            nodeId = nodeId,
            hash = hash
        )

        every { cacheRepository.findByNodeIdAndHash(nodeId, hash) } returns nodeInfo

        // Act
        val result = underTest.getContentId(nodeId, hash)

        // Assert
        assert(result == contentId)

        verify(exactly = 1) { cacheRepository.findByNodeIdAndHash(nodeId, hash) }
        confirmVerified(cacheRepository)
    }

    @Test
    fun testGetContentIdReturnsNullIfNothingFound() {
        // Arrange
        val nodeId = "node125"
        val hash = "hash125"
        every { cacheRepository.findByNodeIdAndHash(nodeId, hash) } returns null

        // Act
        val result = underTest.getContentId(nodeId, hash)

        // Assert
        assert(result == null)
        verify(exactly = 1) { cacheRepository.findByNodeIdAndHash(nodeId, hash) }
        confirmVerified(cacheRepository)
    }
}