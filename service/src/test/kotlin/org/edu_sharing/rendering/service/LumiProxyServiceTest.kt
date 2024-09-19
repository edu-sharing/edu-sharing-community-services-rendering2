package org.edu_sharing.rendering.service

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import jakarta.servlet.http.HttpServletRequest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.edu_sharing.rendering.modules.h5p.lumi.LumiProxyService
import org.edu_sharing.rendering.processing.h5p.LumiNodeInfo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClient
import java.util.Collections

@ExtendWith(MockKExtension::class)
class LumiProxyServiceTest {
    private lateinit var mockServer: MockWebServer
    private lateinit var lumiWebClient: WebClient

    private lateinit var underTest: LumiProxyService

    @BeforeEach
    fun setup() {
        mockServer = MockWebServer()
        mockServer.start()
        lumiWebClient = WebClient.builder()
            .baseUrl(mockServer.url("/").toString())
            .build()
        underTest = LumiProxyService(lumiWebClient)
        clearAllMocks()
    }

    @AfterEach
    fun tearDown() {
        mockServer.shutdown()
        clearAllMocks()
    }

    @Test
    fun testGetProxyRequestReturnsProperResponseAndRoutesRequestCorrectly() {

        // Arrange
        val uri = "/h5p/some/path/test"

        val pathPrefix = "/h5p"
        val nodeInfo = mockk<LumiNodeInfo>()
        val body = null
        val method = HttpMethod.GET
        val request = mockk<HttpServletRequest>()
        val traceId = "traceid"
        val responseType = String::class.java

        val requestHeaders = HttpHeaders()
        requestHeaders[HttpHeaders.USER_AGENT] = "somefakeagent"
        requestHeaders[HttpHeaders.ACCEPT_ENCODING] = "UTF-8"
        val headerIterator = Collections.enumeration(requestHeaders.keys)

        every { request.requestURI } returns uri
        every { request.headerNames } returns headerIterator
        every { request.queryString } returns "querystring"
        every { request.getHeader("User-Agent") } returns "somefakeagent"
        every { request.getHeader("Accept-Encoding") } returns "UTF-8"

        val serverResponse = "testresponse"

        mockServer.enqueue(
            MockResponse()
                .setBody(serverResponse)
                .setHeader("Content-Type", "application/pdf")
                .setHeader("Content-Length", serverResponse.toByteArray().size.toString())
                .setResponseCode(200)
        )

        // Act
        val result = underTest.processProxyRequest(
            pathPrefix = pathPrefix,
            nodeInfo = nodeInfo,
            body = body,
            method = method,
            request = request,
            traceId = traceId,
            responseType = responseType,
        )

        // Assert
        assert(result.headers.containsKey(HttpHeaders.CONTENT_TYPE))
        assert(result.headers.containsKey(HttpHeaders.CONTENT_LENGTH))
        assert(result.statusCode == HttpStatus.OK)
        assert(result.body == "testresponse")

        val mockRequest = mockServer.takeRequest()
        assert(mockRequest.path == "/some/path/test?querystring")
        assert(mockRequest.method == HttpMethod.GET.name())
    }

    @Test
    fun testGetProxyRequestReturnsProperResponseAndRoutesRequestCorrectlyWithBody() {

        // Arrange
        val uri = "/h5p/some/path/test"

        val pathPrefix = "/h5p"
        val nodeInfo = mockk<LumiNodeInfo>()
        val body = "requestbody"
        val method = HttpMethod.POST
        val request = mockk<HttpServletRequest>()
        val traceId = "traceid"
        val responseType = String::class.java

        val requestHeaders = HttpHeaders()
        requestHeaders[HttpHeaders.USER_AGENT] = "somefakeagent"
        requestHeaders[HttpHeaders.ACCEPT_ENCODING] = "UTF-8"
        val headerIterator = Collections.enumeration(requestHeaders.keys)

        every { request.requestURI } returns uri
        every { request.headerNames } returns headerIterator
        every { request.queryString } returns "querystring"
        every { request.getHeader("User-Agent") } returns "somefakeagent"
        every { request.getHeader("Accept-Encoding") } returns "UTF-8"

        val serverResponse = "testresponse"

        mockServer.enqueue(
            MockResponse()
                .setBody(serverResponse)
                .setHeader("Content-Type", "application/pdf")
                .setHeader("Content-Length", serverResponse.toByteArray().size.toString())
                .setResponseCode(200)
        )

        // Act
        val result = underTest.processProxyRequest(
            pathPrefix = pathPrefix,
            nodeInfo = nodeInfo,
            body = body,
            method = method,
            request = request,
            traceId = traceId,
            responseType = responseType,
        )

        // Assert
        assert(result.headers.containsKey(HttpHeaders.CONTENT_TYPE))
        assert(result.headers.containsKey(HttpHeaders.CONTENT_LENGTH))
        assert(result.statusCode == HttpStatus.OK)
        assert(result.body == "testresponse")

        val mockRequest = mockServer.takeRequest()
        assert(mockRequest.path == "/some/path/test?querystring")
        assert(mockRequest.method == HttpMethod.POST.name())
        assert(mockRequest.body.toString() == "[text=requestbody]")
    }
}