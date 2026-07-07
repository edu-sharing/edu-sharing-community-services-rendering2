package org.edu_sharing.rendering.modules.h5p.lumi

import io.micrometer.tracing.Span
import io.micrometer.tracing.TraceContext
import io.micrometer.tracing.Tracer
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import jakarta.servlet.http.HttpServletRequest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.edu_sharing.rendering.modules.h5p.lumi.dto.LumiNodeInfo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClient
import java.util.*

@ExtendWith(MockKExtension::class)
class LumiProxyServiceTest {
    private lateinit var mockServer: MockWebServer
    private lateinit var lumiWebClient: WebClient
    private lateinit var tracer: Tracer

    private lateinit var underTest: LumiProxyService

    @BeforeEach
    fun setup() {
        mockServer = MockWebServer()
        mockServer.start()
        lumiWebClient = WebClient.builder()
            .baseUrl(mockServer.url("/").toString())
            .build()
        tracer = mockk(relaxed = true)
        underTest = LumiProxyService(lumiWebClient, tracer)
        clearAllMocks()
        // No active span by default -> traceId is null and no TRACE header is added.
        every { tracer.currentSpan() } returns null
    }

    @AfterEach
    fun tearDown() {
        mockServer.shutdown()
        clearAllMocks()
    }

    /** Builds an [HttpServletRequest] mock exposing the given URI, headers and query string. */
    private fun mockRequest(
        uri: String,
        headers: Map<String, String> = emptyMap(),
        queryString: String? = null,
    ): HttpServletRequest {
        val request = mockk<HttpServletRequest>()
        every { request.requestURI } returns uri
        every { request.headerNames } returns Collections.enumeration(headers.keys)
        headers.forEach { (name, value) -> every { request.getHeader(name) } returns value }
        every { request.queryString } returns queryString
        return request
    }

    /** A [LumiProxyService] whose tracer reports the given active trace id. */
    private fun serviceWithTraceId(traceId: String): LumiProxyService {
        val tracerWithSpan = mockk<Tracer>()
        val span = mockk<Span>()
        val context = mockk<TraceContext>()
        every { tracerWithSpan.currentSpan() } returns span
        every { span.context() } returns context
        every { context.traceId() } returns traceId
        return LumiProxyService(lumiWebClient, tracerWithSpan)
    }

    private fun enqueue(body: String = "testresponse", vararg headers: Pair<String, String>) {
        val response = MockResponse().setBody(body).setResponseCode(200)
        response.setHeader("Content-Length", body.toByteArray().size.toString())
        headers.forEach { (name, value) -> response.setHeader(name, value) }
        mockServer.enqueue(response)
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

        val requestHeaders = HttpHeaders()
        requestHeaders[HttpHeaders.USER_AGENT] = "somefakeagent"
        requestHeaders[HttpHeaders.ACCEPT_ENCODING] = "UTF-8"
        val headerIterator = Collections.enumeration(requestHeaders.headerNames())

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
        )

        // Assert
        assert(result.headers.containsHeader(HttpHeaders.CONTENT_TYPE))
        assert(result.headers.containsHeader(HttpHeaders.CONTENT_LENGTH))
        assert(result.statusCode == HttpStatus.OK)
        assert(result.body!!.inputStream.bufferedReader().readText() == "testresponse")

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

        val requestHeaders = HttpHeaders()
        requestHeaders[HttpHeaders.USER_AGENT] = "somefakeagent"
        requestHeaders[HttpHeaders.ACCEPT_ENCODING] = "UTF-8"
        val headerIterator = Collections.enumeration(requestHeaders.headerNames())

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
        )

        // Assert
        assert(result.headers.containsHeader(HttpHeaders.CONTENT_TYPE))
        assert(result.headers.containsHeader(HttpHeaders.CONTENT_LENGTH))
        assert(result.statusCode == HttpStatus.OK)
        assert(result.body!!.inputStream.bufferedReader().readText() == "testresponse")

        val mockRequest = mockServer.takeRequest()
        assert(mockRequest.path == "/some/path/test?querystring")
        assert(mockRequest.method == HttpMethod.POST.name())
        assert(mockRequest.body.toString() == "[text=requestbody]")
    }

    @Test
    fun testLargeResponseIsStreamedAndNotBoundedByTheInMemoryCodecLimit() {
        // Arrange: a body far larger than the WebClient default in-memory codec limit (256KB).
        // The previous buffering implementation (.toEntity(ByteArray).block()) would fail here
        // with a DataBufferLimitException; the streaming implementation must pass it through.
        val uri = "/h5p/some/large/asset.bin"
        val pathPrefix = "/h5p"
        val nodeInfo = mockk<LumiNodeInfo>()
        val request = mockk<HttpServletRequest>()

        val requestHeaders = HttpHeaders()
        requestHeaders[HttpHeaders.USER_AGENT] = "somefakeagent"
        val headerIterator = Collections.enumeration(requestHeaders.headerNames())
        every { request.requestURI } returns uri
        every { request.headerNames } returns headerIterator
        every { request.queryString } returns null
        every { request.getHeader("User-Agent") } returns "somefakeagent"

        val largeBody = "a".repeat(2 * 1024 * 1024) // 2 MB
        mockServer.enqueue(
            MockResponse()
                .setBody(largeBody)
                .setHeader("Content-Type", "application/octet-stream")
                .setHeader("Content-Length", largeBody.toByteArray().size.toString())
                .setResponseCode(200)
        )

        // Act
        val result = underTest.processProxyRequest(
            pathPrefix = pathPrefix,
            nodeInfo = nodeInfo,
            body = null,
            method = HttpMethod.GET,
            request = request,
        )

        // Assert: the full body streams through intact.
        assert(result.statusCode == HttpStatus.OK)
        val streamed = result.body!!.inputStream.bufferedReader().readText()
        assert(streamed.length == largeBody.length)
        assert(streamed == largeBody)
    }

    @Test
    fun testForwardsInboundRequestHeadersAndStripsAcceptEncoding() {
        // Arrange
        val request = mockRequest(
            uri = "/h5p/some/path/test",
            headers = mapOf(
                HttpHeaders.USER_AGENT to "somefakeagent",
                "X-Foo" to "bar",
                HttpHeaders.ACCEPT_ENCODING to "caller-supplied-encoding",
            ),
        )
        enqueue()

        // Act
        val result = underTest.processProxyRequest(
            pathPrefix = "/h5p",
            nodeInfo = mockk<LumiNodeInfo>(),
            body = null,
            method = HttpMethod.GET,
            request = request,
        )

        // Assert: inbound headers reach Lumi verbatim...
        val forwarded = mockServer.takeRequest()
        assert(forwarded.getHeader(HttpHeaders.USER_AGENT) == "somefakeagent")
        assert(forwarded.getHeader("X-Foo") == "bar")
        // ...but the caller's Accept-Encoding is stripped, so its value never reaches Lumi.
        // (The reactive client transport negotiates its own Accept-Encoding on the wire; we only
        // guarantee the client-supplied value is dropped, which is what the code controls.)
        assert(forwarded.getHeader(HttpHeaders.ACCEPT_ENCODING) != "caller-supplied-encoding")
        // No active span -> no TRACE header on the forwarded request nor on the response.
        assert(forwarded.getHeader("TRACE") == null)
        assert(result.headers.getFirst("TRACE") == null)
    }

    @Test
    fun testInjectsTraceHeaderIntoRequestAndResponseWhenSpanActive() {
        // Arrange
        val service = serviceWithTraceId("trace-1234")
        val request = mockRequest(
            uri = "/h5p/some/path/test",
            headers = mapOf(HttpHeaders.USER_AGENT to "somefakeagent"),
        )
        enqueue()

        // Act
        val result = service.processProxyRequest(
            pathPrefix = "/h5p",
            nodeInfo = mockk<LumiNodeInfo>(),
            body = null,
            method = HttpMethod.GET,
            request = request,
        )

        // Assert
        val forwarded = mockServer.takeRequest()
        assert(forwarded.getHeader("TRACE") == "trace-1234")
        assert(result.headers.getFirst("TRACE") == "trace-1234")
    }

    @Test
    fun testForwardsUpstreamResponseHeadersToClient() {
        // Arrange
        val request = mockRequest(uri = "/h5p/some/path/test")
        enqueue(headers = arrayOf("X-Upstream" to "up-value", "Content-Type" to "application/pdf"))

        // Act
        val result = underTest.processProxyRequest(
            pathPrefix = "/h5p",
            nodeInfo = mockk<LumiNodeInfo>(),
            body = null,
            method = HttpMethod.GET,
            request = request,
        )

        // Assert
        assert(result.headers.getFirst("X-Upstream") == "up-value")
        assert(result.headers.getFirst(HttpHeaders.CONTENT_TYPE) == "application/pdf")
    }

    @Test
    fun testAppliesAdditionalHeadersToResponse() {
        // Arrange
        val request = mockRequest(uri = "/h5p/some/path/test")
        enqueue()

        // Act
        val result = underTest.processProxyRequest(
            pathPrefix = "/h5p",
            nodeInfo = mockk<LumiNodeInfo>(),
            body = null,
            method = HttpMethod.GET,
            request = request,
            additionalHeaders = mapOf("Content-Security-Policy" to "frame-ancestors *"),
        )

        // Assert
        assert(result.headers.getFirst("Content-Security-Policy") == "frame-ancestors *")
    }

    @Test
    fun testAdditionalHeadersOverrideCollidingUpstreamHeaders() {
        // Arrange
        val request = mockRequest(uri = "/h5p/some/path/test")
        enqueue(headers = arrayOf("X-Override" to "upstream"))

        // Act
        val result = underTest.processProxyRequest(
            pathPrefix = "/h5p",
            nodeInfo = mockk<LumiNodeInfo>(),
            body = null,
            method = HttpMethod.GET,
            request = request,
            additionalHeaders = mapOf("X-Override" to "overridden"),
        )

        // Assert: additionalHeaders are set (not added), overriding the colliding upstream value.
        assert(result.headers["X-Override"] == listOf("overridden"))
    }
}