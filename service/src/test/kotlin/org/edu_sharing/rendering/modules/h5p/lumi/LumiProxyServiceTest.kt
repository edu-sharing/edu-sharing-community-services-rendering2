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
import org.edu_sharing.rendering.core.exception.ModuleGatewayException
import org.edu_sharing.rendering.core.resilience.RemoteModuleUnavailableException
import org.edu_sharing.rendering.core.resilience.ResilienceProperties
import org.edu_sharing.rendering.core.resilience.testGuardRegistry
import org.edu_sharing.rendering.modules.h5p.lumi.dto.LumiNodeInfo
import okhttp3.mockwebserver.SocketPolicy
import org.junit.jupiter.api.assertThrows
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.time.Duration
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
        underTest = LumiProxyService(lumiWebClient, tracer, testGuardRegistry(), LumiProxyProperties())
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
        return LumiProxyService(lumiWebClient, tracerWithSpan, testGuardRegistry(), LumiProxyProperties())
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
                HttpHeaders.RANGE to "bytes=0-9",
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
        assert(forwarded.getHeader(HttpHeaders.RANGE) == "bytes=0-9")
        // ...but the caller's Accept-Encoding is stripped, so its value never reaches Lumi.
        // (The reactive client transport negotiates its own Accept-Encoding on the wire; we only
        // guarantee the client-supplied value is dropped, which is what the code controls.)
        assert(forwarded.getHeader(HttpHeaders.ACCEPT_ENCODING) != "caller-supplied-encoding")
        // No active span -> no TRACE header on the forwarded request nor on the response.
        assert(forwarded.getHeader("TRACE") == null)
        assert(result.headers.getFirst("TRACE") == null)
    }

    @Test
    fun testDoesNotForwardCredentialOrHopHeaders() {
        // Arrange: the caller's JWT, session cookie and Host must never reach Lumi.
        val request = mockRequest(
            uri = "/h5p/some/path/test",
            headers = mapOf(
                HttpHeaders.USER_AGENT to "somefakeagent",
                HttpHeaders.AUTHORIZATION to "Bearer secret.jwt.value",
                HttpHeaders.COOKIE to "SESSION=abc",
                HttpHeaders.HOST to "evil.example.org",
                "X-Foo" to "bar",
            ),
        )
        enqueue()

        // Act
        underTest.processProxyRequest(
            pathPrefix = "/h5p",
            nodeInfo = mockk<LumiNodeInfo>(),
            body = null,
            method = HttpMethod.GET,
            request = request,
        )

        // Assert
        val forwarded = mockServer.takeRequest()
        assert(forwarded.getHeader(HttpHeaders.USER_AGENT) == "somefakeagent")
        assert(forwarded.getHeader(HttpHeaders.AUTHORIZATION) == null)
        assert(forwarded.getHeader(HttpHeaders.COOKIE) == null)
        assert(forwarded.getHeader("X-Foo") == null)
        assert(forwarded.getHeader(HttpHeaders.HOST) != "evil.example.org")
    }

    @Test
    fun testFilterForwardedHeadersIsCaseInsensitive() {
        val request = mockRequest(
            uri = "/h5p/x",
            headers = mapOf("RANGE" to "bytes=1-2", "if-none-match" to "\"etag\"", "Cookie" to "a=b"),
        )
        val result = LumiProxyService.filterForwardedHeaders(request)
        assert(result.getFirst("range") == "bytes=1-2")
        assert(result.getFirst("If-None-Match") == "\"etag\"")
        assert(!result.containsHeader("Cookie"))
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

    // ── failure mapping and guard ─────────────────────────────────────────────────────────────────

    private fun proxy(service: LumiProxyService, request: HttpServletRequest = mockRequest("/h5p/some/path/test")) =
        service.processProxyRequest("/h5p", null, HttpMethod.GET, request)

    private fun serviceWith(
        headersTimeout: Duration = Duration.ofSeconds(20),
        configure: ResilienceProperties.() -> Unit = {},
    ) = LumiProxyService(
        lumiWebClient, tracer, testGuardRegistry(configure = configure),
        LumiProxyProperties().apply { responseHeadersTimeout = headersTimeout }
    )

    @Test
    fun testConnectionErrorIsMappedTo502() {
        // Arrange: nothing listens any more
        mockServer.shutdown()

        // Act
        val thrown = assertThrows<ModuleGatewayException> { proxy(underTest) }

        // Assert
        assert(thrown.status == HttpStatus.BAD_GATEWAY)
    }

    @Test
    fun testHeaderTimeoutIsMappedTo504() {
        // Arrange: lumi accepts the request but never answers
        mockServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))

        // Act
        val thrown = assertThrows<ModuleGatewayException> { proxy(serviceWith(Duration.ofMillis(300))) }

        // Assert
        assert(thrown.status == HttpStatus.GATEWAY_TIMEOUT)
    }

    @Test
    fun testOpenBreakerAnswersWithRemoteModuleUnavailableAndRetryAfter() {
        // Arrange: one connection failure opens the breaker of the proxy guard
        val service = serviceWith {
            modules = mapOf("lumi-proxy" to ResilienceProperties.ModuleOverride().apply { failureThreshold = 1 })
        }
        mockServer.enqueue(MockResponse().setResponseCode(503))
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("never"))

        // Act
        val first = assertThrows<ModuleGatewayException> { proxy(service) }
        val second = assertThrows<RemoteModuleUnavailableException> { proxy(service) }

        // Assert: a 503 of lumi is passed on as 503; then the breaker rejects without contacting lumi
        assert(first.status == HttpStatus.SERVICE_UNAVAILABLE)
        assert(second.retryAfter != null && second.retryAfter!! > Duration.ZERO && second.retryAfter!! <= Duration.ofSeconds(10))
        assert(mockServer.requestCount == 1)
    }

    @Test
    fun testRepeatedHttp500DoesNotOpenTheProxyBreaker() {
        // Arrange: a single broken content reloaded again and again
        val service = serviceWith {
            modules = mapOf("lumi-proxy" to ResilienceProperties.ModuleOverride().apply { failureThreshold = 2 })
        }
        repeat(5) { mockServer.enqueue(MockResponse().setResponseCode(500)) }

        // Act and assert: every call reaches lumi and fails with lumi's own 500 (not a breaker rejection)
        repeat(5) { assertThrows<WebClientResponseException> { proxy(service) } }
        assert(mockServer.requestCount == 5)
    }

    @Test
    fun testProxyRequestsAreNotRetried() {
        // Arrange
        mockServer.enqueue(MockResponse().setResponseCode(503))
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("late"))

        // Act
        assertThrows<ModuleGatewayException> { proxy(underTest) }

        // Assert
        assert(mockServer.requestCount == 1)
    }
}
