package org.edu_sharing.rendering.modules

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verifySequence
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.edusharingRepo.services.ContentTransferService
import org.edu_sharing.rendering.storage.StorageService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.UUID

@ExtendWith(MockKExtension::class)
class ConverterWebServiceCallerTest {
    private lateinit var mockServer: MockWebServer
    private lateinit var webClient: WebClient

    private val dummyCacheObjectWord = CacheObject(
        nodeId = "test_node_id",
        hash = "test_hash",
        type = "doc",
        mimeType = "application/msword",
        repoId = "repo123"
    )

    private val contentTransferService = mockk<ContentTransferService>()
    private val storageImplementation = mockk<StorageService>()

    lateinit var underTest: ConverterWebServiceCaller

    @BeforeEach
    fun setup() {
        mockServer = MockWebServer()
        mockServer.start()
        webClient = WebClient.builder()
            .baseUrl(mockServer.url("/").toString())
            .build()
        underTest = ConverterWebServiceCaller(
            contentTransferService = contentTransferService,
            storageImplementation = storageImplementation
        )
    }

    @AfterEach
    fun teardown() {
        clearAllMocks()
        mockServer.shutdown()
    }

    private fun arguments(externalPath: String) = ConverterWebServiceArguments(
        client = webClient,
        originalFileExtension = ".doc",
        targetMimeType = "application/pdf",
        cacheObject = dummyCacheObjectWord,
        externalServiceMethodPath = externalPath,
        urlParams = mapOf("testParam" to "true")
    )

    @Test
    fun testCallConverterServiceCallsConverterWithProperParamsAndCachesResultWithKnownSize() {
        // Arrange
        val externalPath = UUID.randomUUID().toString()
        val dummyFileData = UUID.randomUUID().toString()
        every { contentTransferService.getAsInputStream(dummyCacheObjectWord) } returns ByteArrayInputStream(
            dummyFileData.toByteArray()
        )

        val expectedContent = "1234ABC"
        mockServer.enqueue(
            MockResponse()
                .addHeader("Content-Type", MediaType.APPLICATION_PDF_VALUE)
                .setBody(expectedContent)
                .setResponseCode(200)
        )

        var uploadedCacheObject: CacheObject? = null
        var uploadedBytes: ByteArray? = null
        // the stream is consumed and closed inside callConverterService, so read it in the answer
        every { storageImplementation.putObject(any(), any<() -> InputStream>()) } answers {
            uploadedCacheObject = firstArg()
            uploadedBytes = secondArg<() -> InputStream>().invoke().readAllBytes()
        }

        // Act
        underTest.callConverterService(arguments(externalPath))

        // Assert
        assert(uploadedBytes!!.toString(Charsets.UTF_8) == expectedContent)
        assert(uploadedCacheObject!!.mimeType == MediaType.APPLICATION_PDF_VALUE)
        assert(uploadedCacheObject!!.size == expectedContent.length.toLong())
        val request = mockServer.takeRequest()
        val body = request.body.readUtf8()
        assert(body.contains(dummyFileData))
        assert(request.path == "/$externalPath")
        assert(request.method == "POST")
        verifySequence {
            contentTransferService.getAsInputStream(dummyCacheObjectWord)
            storageImplementation.putObject(any(), any<() -> InputStream>())
        }
    }

    @Test
    fun testCallConverterServiceThrowsOnEmptyResponse() {
        // Arrange
        val externalPath = UUID.randomUUID().toString()
        every { contentTransferService.getAsInputStream(dummyCacheObjectWord) } returns ByteArrayInputStream(
            UUID.randomUUID().toString().toByteArray()
        )
        mockServer.enqueue(
            MockResponse()
                .addHeader("Content-Type", MediaType.APPLICATION_PDF_VALUE)
                .setBody("")
                .setResponseCode(200)
        )

        // Act and assert
        val exception = assertThrows<Exception> { underTest.callConverterService(arguments(externalPath)) }
        assert(exception.message == "Empty data returned")
    }

    @Test
    fun testCallConverterServiceThrowsExceptionThrownByStorageImplementation() {
        // Arrange
        val externalPath = UUID.randomUUID().toString()
        every { contentTransferService.getAsInputStream(dummyCacheObjectWord) } returns ByteArrayInputStream(
            UUID.randomUUID().toString().toByteArray()
        )
        mockServer.enqueue(
            MockResponse()
                .addHeader("Content-Type", MediaType.APPLICATION_PDF_VALUE)
                .setBody("1234ABC")
                .setResponseCode(200)
        )
        every { storageImplementation.putObject(any(), any<() -> InputStream>()) } throws Exception()

        // Act and assert
        assertThrows<Exception> { underTest.callConverterService(arguments(externalPath)) }
    }
}
