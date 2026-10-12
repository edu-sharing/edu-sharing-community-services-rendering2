package org.edu_sharing.rendering.modules.h5p.lumi

import io.micrometer.tracing.Tracer
import jakarta.servlet.http.HttpServletRequest
import org.apache.commons.lang3.StringUtils
import org.edu_sharing.rendering.core.annotation.ConditionalOnController
import org.edu_sharing.rendering.core.exception.ResourceNotFoundException
import org.edu_sharing.rendering.modules.h5p.lumi.dto.LumiNodeInfo
import org.edu_sharing.rendering.utils.FluxInputStream
import org.slf4j.LoggerFactory
import org.springframework.core.io.InputStreamResource
import org.springframework.core.io.Resource
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.toEntityFlux
import org.springframework.web.util.UriComponentsBuilder
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import reactor.core.Exceptions
import reactor.core.publisher.Flux
import java.time.Duration
import java.util.concurrent.TimeoutException

@ConditionalOnController
@Service
class LumiProxyService(
    private val lumiWebClient: WebClient,
    private val tracer: Tracer
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        /** Upper bound for lumi to deliver status + headers (the body streams afterwards and is not covered). */
        private val RESPONSE_HEADERS_TIMEOUT: Duration = Duration.ofSeconds(120)

        /**
         * Request headers that may be forwarded to lumi (lower case). Everything else - notably `Cookie`,
         * `Authorization` (the caller's JWT), `Host` and hop-by-hop headers - stays inside this service.
         * `Accept-Encoding` is deliberately absent: the proxy streams the body as-is and must not receive
         * a compressed payload it does not re-negotiate. `Content-Length` is absent as the client derives it
         * from the body it actually sends.
         */
        internal val FORWARDED_REQUEST_HEADERS: Set<String> = setOf(
            "accept", "accept-language", "content-type", "range", "if-range", "if-match", "if-none-match",
            "if-modified-since", "if-unmodified-since", "user-agent", "cache-control", "origin", "referer",
            "x-requested-with",
        )

        internal fun filterForwardedHeaders(request: HttpServletRequest): HttpHeaders {
            val headers = HttpHeaders()
            val headerNames = request.headerNames ?: return headers
            while (headerNames.hasMoreElements()) {
                val headerName = headerNames.nextElement()
                if (headerName.lowercase() in FORWARDED_REQUEST_HEADERS) {
                    request.getHeader(headerName)?.let { headers.set(headerName, it) }
                }
            }
            return headers
        }
    }

    @PreAuthorize("hasPermission(#nodeInfo.nodeId, 'ReadAll')")
    fun processProxyRequest(
        pathPrefix: String,
        nodeInfo: LumiNodeInfo,
        body: String?,
        method: HttpMethod,
        request: HttpServletRequest,
        additionalHeaders: Map<String, String> = emptyMap()
    ): ResponseEntity<Resource> {
        return processProxyRequest(
            pathPrefix = pathPrefix,
            body = body,
            method = method,
            request = request,
            additionalHeaders = additionalHeaders
        )
    }

    fun processProxyRequest(
        pathPrefix: String,
        body: String?,
        method: HttpMethod,
        request: HttpServletRequest,
        additionalHeaders: Map<String, String> = emptyMap()
    ): ResponseEntity<Resource> {
        // Trace context (and the b3 header) is propagated automatically by the instrumented lumiWebClient;
        // the explicit TRACE header is kept for the Lumi service and sourced from the active trace id.
        val traceId = tracer.currentSpan()?.context()?.traceId()
        log.debug("Lumi proxy forward: method={}, path={}, traceId={}", method.name(), request.requestURI, traceId)

        val requestURIPathSegments =
            request.requestURI.substringAfter(pathPrefix).split("/").stream().filter { StringUtils.isNotBlank(it) }
                .toList()

        val requestURIPath = requestURIPathSegments.joinToString(separator = "/", prefix = "/")

        val headers = filterForwardedHeaders(request)

        traceId?.let { headers.set("TRACE", it) }

        val lumiRequest = lumiWebClient.method(method)
            .uri {
                UriComponentsBuilder.fromUri(it.build())
                    .path(requestURIPath)
                    .query(request.queryString)
                    .build(true)
                    .toUri()
            }
            .headers { h -> h.addAll(headers) }
        if (body != null) {
            lumiRequest.body(BodyInserters.fromValue(body))
        }
        // toEntityFlux resolves the upstream status + headers eagerly but leaves the body as a
        // lazy Flux<DataBuffer>, so block() waits only for the response headers — never the body.
        // The body is then bridged to a blocking InputStream over a bounded pipe (constant heap
        // per request) instead of being buffered whole as a ByteArray/String.
        val lumiResponse = try {
            lumiRequest.retrieve()
                .toEntityFlux<DataBuffer>()
                .timeout(RESPONSE_HEADERS_TIMEOUT)
                .block()
        } catch (exception: RuntimeException) {
            if (Exceptions.unwrap(exception) is TimeoutException) {
                log.warn("Lumi did not answer {} {} within {}", method.name(), requestURIPath, RESPONSE_HEADERS_TIMEOUT)
                throw ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "Lumi did not respond in time")
            }
            throw exception
        }
            ?: throw ResourceNotFoundException("Called lumi with ${method.name()} $requestURIPath ${request.queryString} $body")
        log.debug("Lumi proxy response: status={}, path={}", lumiResponse.statusCode, requestURIPath)
        val responseHeaders = HttpHeaders()
        responseHeaders.addAll(lumiResponse.headers)
        traceId?.let { responseHeaders.set("TRACE", it) }
        additionalHeaders.forEach { (key, value) -> responseHeaders.set(key, value) }

        val stream = FluxInputStream.toInputStream(lumiResponse.body ?: Flux.empty())
        return ResponseEntity
            .status(lumiResponse.statusCode)
            .headers(responseHeaders)
            .body(InputStreamResource(stream))
    }
}
