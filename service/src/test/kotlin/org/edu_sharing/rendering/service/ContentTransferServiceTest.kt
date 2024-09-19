package org.edu_sharing.rendering.service

import io.mockk.*
import io.mockk.junit5.MockKExtension
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.edu_sharing.rendering.dto.CacheObject
import org.edu_sharing.rendering.edusharingRepo.ContentTransferService
import org.edu_sharing.rendering.edusharingRepo.PrivatePublicKeyService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.core.io.Resource
import org.springframework.core.io.ResourceLoader
import org.springframework.web.reactive.function.client.WebClient
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.*

@ExtendWith(MockKExtension::class)
class ContentTransferServiceTest {
    private val privatePublicKeyService = mockk<PrivatePublicKeyService>()
    private val resourceLoader = mockk<ResourceLoader>()
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
            eduSharingWebClient = eduSharingWebClient,
            privatePublicKeyService = privatePublicKeyService
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
        val cacheObject = mockk<CacheObject>()
        val resource = mockk<Resource>()
        val stream = "123".byteInputStream()
        every { cacheObject.nodeId } returns "TEST_node1"
        every { resourceLoader.getResource("classpath:node1") } returns resource
        every { resource.inputStream } returns stream

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
        val cacheObject = mockk<CacheObject>()
        val serverResponse = "test"

        // Generate keyPair
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048)
        val keyPair = generator.generateKeyPair()

        every { cacheObject.nodeId } returns "node1"
        every { cacheObject.repoId } returns "repo1"
        every { cacheObject.version } returns "1.2"
        every { privatePublicKeyService.getPrivateKey() } returns keyPair.private

        excludeRecords {
            cacheObject.nodeId
            cacheObject.version
            cacheObject.version
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
            val signed = Base64.getDecoder().decode(encodedAuthParam)
            val timeStamp = requestUrl.queryParameter("timeStamp")
            val nodeId = requestUrl.queryParameter("nodeId")
            val verify = Signature.getInstance("SHA1withRSA")
            verify.initVerify(keyPair.public)
            verify.update("$nodeId$timeStamp".toByteArray())
            assert(verify.verify(signed))

            // Check remaining query params
            assert(requestUrl.queryParameter("repId") == "repo1")
            assert(requestUrl.queryParameter("appId") == "renderer2")
            assert(requestUrl.queryParameter("version") == "1.2")
        }

        verify(exactly = 1) {privatePublicKeyService.getPrivateKey()}
        confirmVerified(privatePublicKeyService, resourceLoader)
    }

    @Test
    fun testGetAsInputStreamReplacesRepoIdAndVersionIfNull() {
        // Arrange
        val cacheObject = mockk<CacheObject>()
        val serverResponse = "test"

        // Generate keyPair
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048)
        val keyPair = generator.generateKeyPair()

        every { cacheObject.nodeId } returns "node1"
        every { cacheObject.repoId } returns "repo123"
        every { cacheObject.version } returns null
        every { privatePublicKeyService.getPrivateKey() } returns keyPair.private

        excludeRecords {
            cacheObject.nodeId
            cacheObject.version
            cacheObject.version
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
            assert(requestUrl.queryParameter("repId") == "")
            assert(requestUrl.queryParameter("version") == "")
        }

        verify(exactly = 1) {privatePublicKeyService.getPrivateKey()}
        confirmVerified(privatePublicKeyService, resourceLoader)
    }
}