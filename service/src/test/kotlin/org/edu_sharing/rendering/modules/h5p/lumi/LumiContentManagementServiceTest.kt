package org.edu_sharing.rendering.modules.h5p.lumi

import io.mockk.clearAllMocks
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.edu_sharing.rendering.cacheCleaner.TrackingEntry
import org.edu_sharing.rendering.core.exception.ResourceNotFoundException
import org.edu_sharing.rendering.modules.h5p.H5pRenderModule
import org.edu_sharing.rendering.modules.h5p.lumi.dto.LumiNodeInfo
import org.edu_sharing.rendering.security.NodeSessionContextRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import tools.jackson.databind.json.JsonMapper
import java.time.Duration

@ExtendWith(MockKExtension::class)
class LumiContentManagementServiceTest {
    private val nodeSessionContextRepo = mockk<NodeSessionContextRepository>()
    private val module = mockk<H5pRenderModule>()

    private lateinit var mockServer: MockWebServer
    private lateinit var underTest: LumiContentManagementService

    @BeforeEach
    fun setup() {
        mockServer = MockWebServer()
        mockServer.start()
        underTest = LumiContentManagementService(
            lumiWebClient = WebClient.builder().baseUrl(mockServer.url("/").toString()).build(),
            nodeSessionContextRepo = nodeSessionContextRepo,
            objectMapper = JsonMapper.builder().build(),
            module = module,
            securityEnabled = false,
            bucketInfoCacheTtl = Duration.ofMinutes(1)
        )
        clearAllMocks()
    }

    @AfterEach
    fun teardown() {
        mockServer.shutdown()
        clearAllMocks()
    }

    private fun enqueueJson(body: String) = mockServer.enqueue(
        MockResponse()
            .addHeader("Content-Type", "application/json; charset=utf-8")
            .setBody(body)
            .setResponseCode(200)
    )

    private fun enqueueStatus(code: Int) = mockServer.enqueue(MockResponse().setResponseCode(code))

    @Test
    fun testGetNodeInfoResolvesNodeAndHashFromLumi() {
        // Arrange
        enqueueJson("""{"nodeId": "nodeId124_hash124"}""")

        // Act
        val result = underTest.getNodeInfo("content123")

        // Assert
        val request = mockServer.takeRequest()
        assertEquals("/edusharing/contentid/content123", request.path)
        assertEquals("GET", request.method)
        assertEquals(LumiNodeInfo(lumiId = "content123", nodeId = "nodeId124", hash = "hash124"), result)
    }

    @Test
    fun testGetNodeInfoServesRepeatedLookupsFromTheLocalCache() {
        // Arrange — one response for two lookups: a second request would leave the client hanging
        enqueueJson("""{"nodeId": "nodeId124_hash124"}""")

        // Act
        val first = underTest.getNodeInfo("content123")
        val second = underTest.getNodeInfo("content123")

        // Assert
        assertEquals(first, second)
        assertEquals(1, mockServer.requestCount)
    }

    @Test
    fun testGetNodeInfoThrowsResourceNotFoundIfLumiHasNoSuchContent() {
        // Arrange
        enqueueStatus(404)

        // Act and assert
        assertThrows<ResourceNotFoundException> { underTest.getNodeInfo("content123") }
    }

    @Test
    fun testGetNodeInfoPropagatesOtherLumiErrors() {
        // Arrange
        enqueueStatus(500)

        // Act and assert
        assertThrows<WebClientResponseException> { underTest.getNodeInfo("content123") }
    }

    @Test
    fun testGetContentIdReturnsContentIdIfLumiHoldsARendering() {
        // Arrange
        enqueueJson("""{"contentId": "content125"}""")

        // Act
        val result = underTest.getContentId("node125", "hash125")

        // Assert
        val request = mockServer.takeRequest()
        assertEquals("/edusharing/nodeid/node125_hash125", request.path)
        assertEquals("GET", request.method)
        assertEquals("content125", result)
    }

    @Test
    fun testGetContentIdReturnsNullIfLumiHoldsNoRendering() {
        // Arrange
        enqueueStatus(404)

        // Act
        val result = underTest.getContentId("node125", "hash125")

        // Assert
        assertNull(result)
    }

    @Test
    fun testGetContentIdPropagatesOtherLumiErrors() {
        // Arrange
        enqueueStatus(500)

        // Act and assert
        assertThrows<WebClientResponseException> { underTest.getContentId("node125", "hash125") }
    }

    @Test
    fun testDeleteContentDeletesAtLumiAndDropsTheCachedNodeInfo() {
        // Arrange — resolve the mapping so that it is cached, then delete the content
        enqueueJson("""{"nodeId": "node126_hash126"}""")
        underTest.getNodeInfo("content126")
        mockServer.takeRequest()
        enqueueStatus(204)

        // Act
        underTest.deleteContent(
            TrackingEntry.of(
                repoId = "repo1",
                nodeId = "node126",
                hash = "hash126",
                type = "h5p",
                bucket = "bucket1",
                binarySize = 1L
            )
        )

        // Assert
        val deleteRequest = mockServer.takeRequest()
        assertEquals("/edusharing/node126_hash126", deleteRequest.path)
        assertEquals("DELETE", deleteRequest.method)

        // the evicted mapping is fetched again instead of being served stale
        enqueueJson("""{"nodeId": "node126_hash126"}""")
        underTest.getNodeInfo("content126")
        assertEquals("/edusharing/contentid/content126", mockServer.takeRequest().path)
    }

    @Test
    fun testGetContentBucketReturnsNameFromLumi() {
        // Arrange
        enqueueJson("""{"contentBucket": "lumi-contentbucket"}""")

        // Act
        val result = underTest.getContentBucket("repo1")

        // Assert
        val request = mockServer.takeRequest()
        assertEquals("/edusharing/buckets/", request.path)
        assertEquals("lumi-contentbucket", result)
    }

    @Test
    fun testGetContentBucketInfoReturnsNameAndQuotaFromLumi() {
        // Arrange
        enqueueJson("""{"contentBucket": "lumi-contentbucket", "contentBucketQuota": 10737418240}""")

        // Act
        val result = underTest.getContentBucketInfo("repo1")

        // Assert
        assertEquals("lumi-contentbucket", result.contentBucket)
        assertEquals(10737418240L, result.contentBucketQuota)
    }

    @Test
    fun testGetContentBucketInfoHasNoQuotaWhenLumiReportsNone() {
        // Arrange — older lumi versions/without quota configured omit the field entirely
        enqueueJson("""{"contentBucket": "lumi-contentbucket"}""")

        // Act
        val result = underTest.getContentBucketInfo("repo1")

        // Assert
        assertNull(result.contentBucketQuota)
    }

    @Test
    fun testGetContentBucketAndGetContentBucketInfoShareOneCachedLumiCallPerRepo() {
        // Arrange — a single response for both getContentBucket and getContentBucketInfo
        enqueueJson("""{"contentBucket": "lumi-contentbucket", "contentBucketQuota": 5368709120}""")

        // Act
        val bucket = underTest.getContentBucket("repo1")
        val info = underTest.getContentBucketInfo("repo1")

        // Assert — both reads served from the same cached bucket info, only one HTTP call
        assertEquals("lumi-contentbucket", bucket)
        assertEquals(5368709120L, info.contentBucketQuota)
        assertEquals(1, mockServer.requestCount)
    }

    @Test
    fun testGetContentBucketRefreshesAfterTtlExpires() {
        // Arrange — a short-lived instance so the test doesn't need to wait for the real default TTL
        val shortLived = LumiContentManagementService(
            lumiWebClient = WebClient.builder().baseUrl(mockServer.url("/").toString()).build(),
            nodeSessionContextRepo = nodeSessionContextRepo,
            objectMapper = JsonMapper.builder().build(),
            module = module,
            securityEnabled = false,
            bucketInfoCacheTtl = Duration.ofMillis(20)
        )
        enqueueJson("""{"contentBucket": "lumi-contentbucket"}""")
        shortLived.getContentBucket("repo1")
        Thread.sleep(50)
        enqueueJson("""{"contentBucket": "lumi-contentbucket-renamed"}""")

        // Act
        val result = shortLived.getContentBucket("repo1")

        // Assert
        assertEquals("lumi-contentbucket-renamed", result)
        assertEquals(2, mockServer.requestCount)
    }
}