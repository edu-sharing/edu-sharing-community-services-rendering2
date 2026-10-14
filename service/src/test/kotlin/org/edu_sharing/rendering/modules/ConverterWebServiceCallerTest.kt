package org.edu_sharing.rendering.modules

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifySequence
import okhttp3.mockwebserver.SocketPolicy
import org.edu_sharing.rendering.core.ErrorStrings
import org.junit.jupiter.api.Assertions.assertEquals
import java.io.File
import java.time.Duration
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
            storageImplementation = storageImplementation,
            properties = fastRetry
        )
    }

    // Short backoffs so the retry tests stay fast; semantics are the same as the production defaults.
    private val fastRetry = ConverterCallProperties().apply {
        maxAttempts = 3
        initialBackoff = Duration.ofMillis(20)
        maxBackoff = Duration.ofMillis(100)
        attemptTimeout = Duration.ofSeconds(10)
        totalBudget = Duration.ofSeconds(30)
        maxRetryAfter = Duration.ofSeconds(2)
    }

    private val tempPrefix = "rendering-test_node_id_test_hash"

    private fun leftoverTempFiles(): List<String> =
        File(System.getProperty("java.io.tmpdir")).list { _, name -> name.startsWith(tempPrefix) }!!.toList()

    private fun stubInput(data: String = UUID.randomUUID().toString()): String {
        every { contentTransferService.getAsInputStream(dummyCacheObjectWord) } answers {
            ByteArrayInputStream(data.toByteArray())
        }
        return data
    }

    private fun stubStorage() {
        every { storageImplementation.putObject(any(), any<() -> InputStream>()) } answers {
            secondArg<() -> InputStream>().invoke().readAllBytes()
        }
    }

    private fun ok(body: String = "1234ABC") = MockResponse()
        .addHeader("Content-Type", MediaType.APPLICATION_PDF_VALUE).setBody(body).setResponseCode(200)

    @Test
    fun testClientErrorsAreNotRetriedAndMapToUserMessages() {
        val cases = mapOf(
            400 to ConverterErrorStrings.DOCUMENT_INVALID,
            422 to ConverterErrorStrings.DOCUMENT_INVALID,
            413 to ConverterErrorStrings.DOCUMENT_TOO_LARGE
        )
        cases.forEach { (status, expectedMessage) ->
            val before = mockServer.requestCount
            stubInput()
            mockServer.enqueue(MockResponse().setResponseCode(status).setBody("nope"))

            val exception = assertThrows<ConverterCallException> { underTest.callConverterService(arguments("p")) }

            assertEquals(expectedMessage, exception.userMessage)
            assertEquals(true, exception.documentProblem)
            assertEquals(status, exception.status)
            assertEquals(1, mockServer.requestCount - before, "status $status must not be retried")
        }
        assertEquals(emptyList<String>(), leftoverTempFiles())
    }

    @Test
    fun testServiceUnavailableHonoursRetryAfterThenSucceedsWithFreshBodyPerAttempt() {
        val data = stubInput()
        stubStorage()
        mockServer.enqueue(MockResponse().setResponseCode(503).addHeader("Retry-After", "1"))
        mockServer.enqueue(ok())

        val started = System.nanoTime()
        underTest.callConverterService(arguments("p"))
        val elapsedMillis = Duration.ofNanos(System.nanoTime() - started).toMillis()

        assertEquals(2, mockServer.requestCount)
        assert(elapsedMillis >= 900) { "Retry-After was not respected: $elapsedMillis ms" }
        // The file is re-read per attempt: both requests carry the full original content.
        repeat(2) { assert(mockServer.takeRequest().body.readUtf8().contains(data)) }
        assertEquals(emptyList<String>(), leftoverTempFiles())
    }

    @Test
    fun testConnectionDropIsRetriedThenSucceeds() {
        stubInput()
        stubStorage()
        mockServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        mockServer.enqueue(ok())

        underTest.callConverterService(arguments("p"))

        assertEquals(2, mockServer.requestCount)
        verify(exactly = 1) { storageImplementation.putObject(any(), any<() -> InputStream>()) }
        assertEquals(emptyList<String>(), leftoverTempFiles())
    }

    @Test
    fun testRetryAfterAttemptsAreExhaustedReportsOperationalFailure() {
        stubInput()
        repeat(3) { mockServer.enqueue(MockResponse().setResponseCode(503)) }

        val exception = assertThrows<ConverterCallException> { underTest.callConverterService(arguments("p")) }

        assertEquals(3, mockServer.requestCount)
        assertEquals(ErrorStrings.GENERIC_CONVERSION_ERROR, exception.userMessage)
        assertEquals(false, exception.documentProblem)
        assertEquals(503, exception.status)
        verify(exactly = 0) { storageImplementation.putObject(any(), any<() -> InputStream>()) }
        assertEquals(emptyList<String>(), leftoverTempFiles())
    }

    @Test
    fun testGenericServerErrorIsNotRetried() {
        stubInput()
        mockServer.enqueue(MockResponse().setResponseCode(500))

        val exception = assertThrows<ConverterCallException> { underTest.callConverterService(arguments("p")) }

        assertEquals(1, mockServer.requestCount)
        assertEquals(ErrorStrings.GENERIC_CONVERSION_ERROR, exception.userMessage)
    }

    @Test
    fun testTotalBudgetStopsRetriesEarly() {
        stubInput()
        underTest = ConverterWebServiceCaller(
            contentTransferService, storageImplementation,
            ConverterCallProperties().apply {
                maxAttempts = 10
                initialBackoff = Duration.ofMillis(200)
                maxBackoff = Duration.ofMillis(200)
                totalBudget = Duration.ofMillis(300)
            }
        )
        repeat(10) { mockServer.enqueue(MockResponse().setResponseCode(503)) }

        assertThrows<ConverterCallException> { underTest.callConverterService(arguments("p")) }

        assert(mockServer.requestCount < 10) { "budget did not stop the retries" }
        assertEquals(emptyList<String>(), leftoverTempFiles())
    }

    @Test
    fun testErrorMessageDoesNotLeakSecretsFromTheRequestUri() {
        stubInput()
        mockServer.enqueue(MockResponse().setResponseCode(422))
        val client = WebClient.builder().baseUrl(mockServer.url("/").toString() + "?token=SECRET").build()

        val exception = assertThrows<ConverterCallException> {
            underTest.callConverterService(arguments("p").copy(client = client))
        }

        assert(!exception.message!!.contains("SECRET")) { exception.message.toString() }
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
        assertEquals(emptyList<String>(), leftoverTempFiles())
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
        assertEquals(emptyList<String>(), leftoverTempFiles())
    }
}
