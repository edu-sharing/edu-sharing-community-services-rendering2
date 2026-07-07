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
import reactor.core.publisher.Flux

@ConditionalOnController
@Service
class LumiProxyService(
    private val lumiWebClient: WebClient,
    private val tracer: Tracer
) {
    private val log = LoggerFactory.getLogger(javaClass)

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

        val headers = HttpHeaders()
        val headerNames = request.headerNames
        while (headerNames.hasMoreElements()) {
            val headerName = headerNames.nextElement()
            headers.set(headerName, request.getHeader(headerName))
        }

        traceId?.let { headers.set("TRACE", it) }
        headers.remove(HttpHeaders.ACCEPT_ENCODING)

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
        val lumiResponse = lumiRequest.retrieve()
            .toEntityFlux<DataBuffer>()
            .block()
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
