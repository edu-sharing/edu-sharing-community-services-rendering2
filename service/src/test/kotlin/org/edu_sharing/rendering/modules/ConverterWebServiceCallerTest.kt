package org.edu_sharing.rendering.modules

import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import okhttp3.mockwebserver.MockWebServer
import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.edusharingRepo.services.ContentTransferService
import org.edu_sharing.rendering.storage.StorageService
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.web.reactive.function.client.WebClient

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

    /*@BeforeEach
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

    @Test
    fun testCallConverterServiceCallsConverterWithProperParamsAndCachesResult() {
        // Arrange
        val externalPath = UUID.randomUUID().toString()
        val arguments = ConverterWebServiceArguments(
            client = webClient,
            originalFileExtension = ".doc",
            targetMimeType = "application/pdf",
            cacheObject = dummyCacheObjectWord,
            externalServiceMethodPath = externalPath,
            urlParams = mapOf("testParam" to "true")
        )

        val dummyFileData = UUID.randomUUID().toString()
        every { contentTransferService.getAsInputStream(dummyCacheObjectWord) } returns ByteArrayInputStream(
            dummyFileData.toByteArray()
        )

        val expectedContent = "1234ABC"
        val mockResponse = MockResponse()
            .addHeader("Content-Type", MediaType.APPLICATION_PDF_VALUE)
            .setBody(expectedContent)
            .setResponseCode(200)
        mockServer.enqueue(mockResponse)

        val expectedConvertedCacheObject = dummyCacheObjectWord.copy()
        expectedConvertedCacheObject.mimeType = MediaType.APPLICATION_PDF_VALUE
        val inputStreamSlot = slot<InputStream>()

        justRun { storageImplementation.putObject(expectedConvertedCacheObject, capture(inputStreamSlot)) }

        // Act
        underTest.callConverterService(arguments)

        // Assert
        assert(inputStreamSlot.captured.readAllBytes().toString(Charsets.UTF_8) == expectedContent)
        val request = mockServer.takeRequest()
        val body = request.body.readUtf8()
        assert(body.contains("name=\"file\"; filename=\"${dummyCacheObjectWord.nodeId}_${dummyCacheObjectWord.hash}.doc\""))
        assert(body.contains(dummyFileData))
        assert(request.path == "/$externalPath")
        assert(request.method == "POST")
        verifySequence{
            contentTransferService.getAsInputStream(dummyCacheObjectWord)
            storageImplementation.putObject(expectedConvertedCacheObject, any())
        }
    }

    @Test
    fun testCallConverterServiceThrowsExceptionThrownByStorageImplementation() {
        // Arrange
        val externalPath = UUID.randomUUID().toString()
        val arguments = ConverterWebServiceArguments(
            client = webClient,
            originalFileExtension = ".doc",
            targetMimeType = "application/pdf",
            cacheObject = dummyCacheObjectWord,
            externalServiceMethodPath = externalPath,
            urlParams = mapOf("testParam" to "true")
        )

        val dummyFileData = UUID.randomUUID().toString()
        every { contentTransferService.getAsInputStream(dummyCacheObjectWord) } returns ByteArrayInputStream(
            dummyFileData.toByteArray()
        )

        val expectedContent = "1234ABC"
        val mockResponse = MockResponse()
            .addHeader("Content-Type", MediaType.APPLICATION_PDF_VALUE)
            .setBody(expectedContent)
            .setResponseCode(200)
        mockServer.enqueue(mockResponse)

        val expectedConvertedCacheObject = dummyCacheObjectWord.copy()
        expectedConvertedCacheObject.mimeType = MediaType.APPLICATION_PDF_VALUE
        val inputStreamSlot = slot<InputStream>()

        every { storageImplementation.putObject(expectedConvertedCacheObject, capture(inputStreamSlot)) } throws Exception()


        // Act and assert
        assertThrows<Exception> { underTest.callConverterService(arguments) }
    }*/
}