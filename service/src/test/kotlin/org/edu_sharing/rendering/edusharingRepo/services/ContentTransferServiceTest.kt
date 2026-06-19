package org.edu_sharing.rendering.edusharingRepo.services

import io.mockk.*
import io.mockk.junit5.MockKExtension
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.edusharingRepo.EncryptionService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.core.io.Resource
import org.springframework.core.io.ResourceLoader
import org.springframework.web.reactive.function.client.WebClient
import java.net.URLDecoder
import java.util.*

@ExtendWith(MockKExtension::class)
class ContentTransferServiceTest {
    private val resourceLoader = mockk<ResourceLoader>()
    private val repositoryRegistrationService = mockk<RepositoryRegistrationService>()
    private val encryptionService = mockk<EncryptionService>()
    private lateinit var eduSharingWebClient: WebClient
    private lateinit var mockServer: MockWebServer

    private lateinit var underTest: ContentTransferService

    @BeforeEach
    fun setup() {
        mockServer = MockWebServer()
        mockServer.start()
        eduSharingWebClient = WebClient.builder()
            .baseUrl(mockServer.url("/").toString())
            .build()
        underTest = ContentTransferService(
            resourceLoader = resourceLoader,
            repoRegistrationService = repositoryRegistrationService,
            encryptionService = encryptionService
        )
        underTest.appId = "renderer2"
    }

    @AfterEach
    fun tearDown() {
        mockServer.shutdown()
        clearAllMocks()
    }

    @Test
    fun testGetAsInputStreamRetrievesTestDataIfTestPrefixSet() {
        // Arrange
        val cacheObject = mockk<CacheObject>(relaxed = true)
        val resource = mockk<Resource>()
        val stream = "123".byteInputStream()
        every { cacheObject.nodeId } returns "TEST_node1"
        every { resourceLoader.getResource("classpath:node1") } returns resource
        every { resource.inputStream } returns stream
        every { resource.contentLength() } returns 1L
        every { cacheObject.size = any() } just Runs

        excludeRecords {
            cacheObject.nodeId
            resource.inputStream
        }

        // Act
        val result = underTest.getAsInputStream(cacheObject)

        // Assert
        assert(result == stream)

        verify(exactly = 1) {
            resourceLoader.getResource("classpath:node1")
        }
        confirmVerified(resourceLoader)
    }

    @Test
    fun testAsInputStreamRetrievesAssetFromRepo() {
        // Arrange
        val cacheObject = mockk<CacheObject>(relaxed = true)
        val serverResponse = "test"
        val repoId = "repo1"

        val signedSlot = slot<String>()

        every { cacheObject.nodeId } returns "node1"
        every { cacheObject.repoId } returns "repo1"
        every { cacheObject.version } returns "1.2"
        every { repositoryRegistrationService.getWebClientByRepoId("repo1") } returns eduSharingWebClient
        every { encryptionService.sign(capture(signedSlot), repoId) } returns "test".toByteArray()
        every { encryptionService.getSigningAlg(repoId) } returns "SHA512withRSA"

        excludeRecords {
            cacheObject.nodeId
            cacheObject.version
            cacheObject.repoId
            cacheObject.repoId
        }

        // Web Server
        mockServer.enqueue(
            MockResponse()
                .setBody(serverResponse)
                .setHeader("Content-Type", "application/pdf")
                .setHeader("Content-Length", serverResponse.toByteArray().size.toString())
                .setResponseCode(200)
        )

        // Act
        val result = underTest.getAsInputStream(cacheObject)

        // Assert
        assert(result.readAllBytes().toString(Charsets.UTF_8) == "test")

        // Check request
        val request = mockServer.takeRequest()
        val requestUrl = request.requestUrl
        assert(requestUrl !== null)
        if (requestUrl !== null) {

            // Check signed auth param against key
            val encodedAuthParam = requestUrl.queryParameter("authToken")
            val decoded = String(Base64.getDecoder().decode(URLDecoder.decode(encodedAuthParam, Charsets.UTF_8)))
            assert(decoded == "test")

            // Check remaining query params
            assert(requestUrl.queryParameter("timeStamp") !== null)
            assert(requestUrl.queryParameter("nodeId") == "node1")
            assert(requestUrl.queryParameter("repId") == "repo1")
            assert(requestUrl.queryParameter("appId") == "renderer2")
            assert(requestUrl.queryParameter("version") == "1.2")
        }

        verifySequence {
            encryptionService.sign(any(), repoId)
            repositoryRegistrationService.getWebClientByRepoId("repo1")
            encryptionService.getSigningAlg(repoId)
        }
        confirmVerified(resourceLoader, repositoryRegistrationService)
    }

    @Test
    fun testGetAsInputStreamReplacesRepoIdAndVersionIfNull() {
        // Arrange
        val cacheObject = mockk<CacheObject>(relaxed = true)
        val serverResponse = "test"
        val repoId = "repo123"

        every { cacheObject.nodeId } returns "node1"
        every { cacheObject.repoId } returns "repo123"
        every { cacheObject.version } returns null
        every { repositoryRegistrationService.getWebClientByRepoId("repo123") } returns eduSharingWebClient
        every { encryptionService.sign(any(), repoId) } returns "test".toByteArray()
        every { encryptionService.getSigningAlg(repoId) } returns "SHA512withRSA"

        excludeRecords {
            cacheObject.nodeId
            cacheObject.version
            cacheObject.version
            cacheObject.repoId
        }

        // Web Server
        mockServer.enqueue(
            MockResponse()
                .setBody(serverResponse)
                .setHeader("Content-Type", "application/pdf")
                .setHeader("Content-Length", serverResponse.toByteArray().size.toString())
                .setResponseCode(200)
        )

        // Act
        val result = underTest.getAsInputStream(cacheObject)

        // Assert
        assert(result.readAllBytes().toString(Charsets.UTF_8) == "test")

        // Check request
        val request = mockServer.takeRequest()
        val requestUrl = request.requestUrl
        assert(requestUrl !== null)
        if (requestUrl !== null) {
            // Check remaining query params
            assert(requestUrl.queryParameter("version") == "")
        }

        verifySequence {
            repositoryRegistrationService.getWebClientByRepoId("repo123")
        }
        confirmVerified( resourceLoader, repositoryRegistrationService)
    }
}
