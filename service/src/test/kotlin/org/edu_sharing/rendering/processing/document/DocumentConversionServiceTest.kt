package org.edu_sharing.rendering.processing.document

import io.mockk.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.edu_sharing.rendering.blobStorage.StorageService
import org.edu_sharing.rendering.dto.CacheObject
import org.edu_sharing.rendering.dto.RenderModules
import org.edu_sharing.rendering.modules.document.DocumentRenderModule
import org.edu_sharing.rendering.service.ContentTransferService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.*


class DocumentConversionServiceTest {
    private lateinit var mockServer: MockWebServer
    private lateinit var webClient: WebClient

    private val dummyCacheObjectWord = CacheObject(
        nodeId = "test_node_id",
        hash = "test_hash",
        type = "doc",
        mimeType = "application/msword"
    )

    private val dummyCacheObjectExcel = CacheObject(
        nodeId = "test_node_id",
        hash = "test_hash",
        type = "doc",
        mimeType = "application/vnd.ms-excel"
    )

    private val dummyCacheObjectWithNonsenseMimeType = CacheObject(
        nodeId = "test_node_id",
        hash = "test_hash",
        type = "doc",
        mimeType = "text/octet-stream"
    )

    // Mock objects
    private val contentTransferService = mockk<ContentTransferService>()
    private val storageImplementation = mockk<StorageService>()
    private val module: DocumentRenderModule = mockk()

    // Class under test
    private lateinit var underTest: DocumentConversionService

    @BeforeEach
    fun setup() {
        mockServer = MockWebServer()
        mockServer.start()
        webClient = WebClient.builder()
            .baseUrl(mockServer.url("/").toString())
            .build()
        underTest = DocumentConversionService(
            contentTransferService, storageImplementation, webClient
        )
    }

    @AfterEach
    fun teardown() {
        mockServer.shutdown()
    }

    @Test
    fun testConvertAndMoveToCacheCallsConverterWithProperParamsAndCachesResult() {
        // Arrange
        val dummyFileData = UUID.randomUUID().toString()
        every { contentTransferService.getAsInputStream(dummyCacheObjectWord) } returns ByteArrayInputStream(
            dummyFileData.toByteArray()
        )
        every { module.getTargetMimetype() } returns MediaType.APPLICATION_PDF_VALUE
        every { module.module() } returns RenderModules.DOCUMENT

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
        underTest.convertAndMoveToCache(dummyCacheObjectWord, module)

        // Assert
        assertThat(inputStreamSlot.captured.readAllBytes().toString(Charsets.UTF_8) == expectedContent)

        val request = mockServer.takeRequest()
        val body = request.body.readUtf8()
        assertThat(body)
            .contains("name=\"file\"; filename=\"${dummyCacheObjectWord.nodeId}_${dummyCacheObjectWord.hash}.doc\"")
        assertThat(body).contains(dummyFileData)
        assertThat(request.path == "/conversion")

        verifySequence {
            contentTransferService.getAsInputStream(dummyCacheObjectWord)
            module.module()
            module.getTargetMimetype()
            storageImplementation.putObject(expectedConvertedCacheObject, any())
        }
        confirmVerified(storageImplementation, module, contentTransferService)
    }

    @Test
    fun testConvertAndMoveToCacheCallsConverterWithProperParamsAndCachesResultWithSpreadsheetActive() {
        // Arrange
        val dummyFileData = UUID.randomUUID().toString()
        every { contentTransferService.getAsInputStream(dummyCacheObjectExcel) } returns ByteArrayInputStream(
            dummyFileData.toByteArray()
        )
        every { module.getTargetMimetype() } returns MediaType.TEXT_HTML_VALUE
        every { module.module() } returns RenderModules.SPREADSHEET

        val expectedContent = "1234ABC"
        val mockResponse = MockResponse()
            .addHeader("Content-Type", MediaType.TEXT_HTML_VALUE)
            .setBody(expectedContent)
            .setResponseCode(200)
        mockServer.enqueue(mockResponse)

        val expectedConvertedCacheObject = dummyCacheObjectExcel.copy()
        expectedConvertedCacheObject.mimeType = MediaType.TEXT_HTML_VALUE
        val inputStreamSlot = slot<InputStream>()

        justRun { storageImplementation.putObject(expectedConvertedCacheObject, capture(inputStreamSlot)) }

        // Act
        underTest.convertAndMoveToCache(dummyCacheObjectExcel, module)

        // Assert
        assertThat(inputStreamSlot.captured.readAllBytes().toString(Charsets.UTF_8) == expectedContent)

        val request = mockServer.takeRequest()
        val body = request.body.readUtf8()
        assertThat(body)
            .contains("name=\"file\"; filename=\"${dummyCacheObjectExcel.nodeId}_${dummyCacheObjectExcel.hash}.xls\"")
        assertThat(body).contains(dummyFileData)
        assertThat(body).contains("name=\"format\"")
        assertThat(body).contains("html")
        assertThat(request.path == "/conversion")

        verifySequence {
            contentTransferService.getAsInputStream(dummyCacheObjectExcel)
            module.module()
            module.getTargetMimetype()
            storageImplementation.putObject(expectedConvertedCacheObject, any())
        }
        confirmVerified(storageImplementation, module, contentTransferService)
    }

    @Test
    fun testConvertAndMoveToCacheThrowsExceptionOnEmptyResponseBody() {
        // Arrange
        val dummyFileData = UUID.randomUUID().toString()
        every { contentTransferService.getAsInputStream(any()) } returns ByteArrayInputStream(
            dummyFileData.toByteArray()
        )
        every { module.getTargetMimetype() } returns MediaType.APPLICATION_PDF_VALUE
        every { module.module() } returns RenderModules.DOCUMENT

        val mockResponse = MockResponse()
            .addHeader("Content-Type", MediaType.APPLICATION_PDF_VALUE)
            .setResponseCode(200)
        mockServer.enqueue(mockResponse)

        // Act and assert
        assertThrows<Exception> {  underTest.convertAndMoveToCache(dummyCacheObjectExcel, module) }

        verifySequence {
            contentTransferService.getAsInputStream(any())
            module.module()
            module.getTargetMimetype()
        }
        confirmVerified(storageImplementation, module, contentTransferService)
    }

    @Test
    fun testConvertAndMoveToCacheThrowsExceptionOnInvalidMimeType() {
        // Arrange
        val dummyFileData = UUID.randomUUID().toString()
        every { contentTransferService.getAsInputStream(any()) } returns ByteArrayInputStream(
            dummyFileData.toByteArray()
        )

        // Act and assert
        assertThrows<Exception> {  underTest.convertAndMoveToCache(dummyCacheObjectWithNonsenseMimeType, module) }

        verifySequence {
            contentTransferService.getAsInputStream(any())
        }
        confirmVerified(contentTransferService)
    }
}