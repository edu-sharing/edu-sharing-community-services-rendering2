package org.edu_sharing.rendering.modules.h5p.lumi

import io.micrometer.tracing.Tracer
import jakarta.servlet.http.HttpServletRequest
import org.apache.commons.lang3.StringUtils
import org.edu_sharing.rendering.core.annotation.ConditionalOnController
import org.edu_sharing.rendering.core.exception.ModuleGatewayException
import org.edu_sharing.rendering.core.exception.ResourceNotFoundException
import org.edu_sharing.rendering.core.resilience.RemoteCallGuardRegistry
import org.edu_sharing.rendering.core.resilience.ResilienceProperties
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
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.Exceptions
import reactor.core.publisher.Flux
import java.time.Duration
import io.netty.handler.timeout.ReadTimeoutException
import java.io.IOException
import java.util.concurrent.TimeoutException

@ConditionalOnController
@Service
class LumiProxyService(
    private val lumiWebClient: WebClient,
    private val tracer: Tracer,
    guardRegistry: RemoteCallGuardRegistry,
    private val properties: LumiProxyProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Own guard, separate from the lookup guard `lumi`: a burst of proxy requests must not lock out the lookups, and
     * a sick lumi must be relieved quickly (breaker). Never retried (user path); only reachability failures count, so
     * one broken H5P content that is reloaded again and again cannot open the breaker for all contents. It wraps just
     * the wait for status + headers - the body streaming afterwards (FluxInputStream) is not covered (accepted).
     */
    private val guard = guardRegistry.guard(ResilienceProperties.LUMI_PROXY_GUARD)

    companion object {
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
        val headersTimeout = properties.responseHeadersTimeout
        val lumiResponse = try {
            guard.block(retryable = false) {
                lumiRequest.retrieve()
                    .toEntityFlux<DataBuffer>()
                    .timeout(headersTimeout)
            }
        } catch (exception: RuntimeException) {
            throw toGatewayFailure(exception, method, requestURIPath, headersTimeout) ?: exception
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

    /**
     * Maps "lumi is not reachable / too slow" to a [ModuleGatewayException] (504 timeout, 502 connection error or a
     * 502/503/504 answer of lumi or a gateway in front of it), which the global handler logs as one WARN line without
     * stack trace. Anything else (including the guard's 503 [org.edu_sharing.rendering.core.resilience.RemoteModuleUnavailableException]
     * and lumi's own 4xx/500) is returned as `null` and rethrown unchanged.
     */
    private fun toGatewayFailure(
        exception: RuntimeException,
        method: HttpMethod,
        path: String,
        headersTimeout: Duration,
    ): ModuleGatewayException? {
        val failure = Exceptions.unwrap(exception)
        val chain = generateSequence<Throwable>(failure) { it.cause?.takeIf { cause -> cause !== it } }.take(10)
        return when {
            chain.any { it is TimeoutException || it is ReadTimeoutException } ->
                ModuleGatewayException(
                    HttpStatus.GATEWAY_TIMEOUT,
                    "Lumi did not answer ${method.name()} $path within $headersTimeout", failure
                )
            failure is WebClientRequestException || failure is IOException ->
                ModuleGatewayException(
                    HttpStatus.BAD_GATEWAY,
                    "Lumi is not reachable for ${method.name()} $path: ${failure.javaClass.simpleName}", failure
                )
            failure is WebClientResponseException && failure.statusCode.value() in 502..504 ->
                ModuleGatewayException(
                    HttpStatus.valueOf(failure.statusCode.value()),
                    "Lumi answered ${method.name()} $path with ${failure.statusCode.value()}", failure,
                    retryAfter = failure.headers.getFirst(HttpHeaders.RETRY_AFTER)?.trim()?.toLongOrNull()
                        ?.takeIf { it >= 0 }?.let { Duration.ofSeconds(it) }
                )
            else -> null
        }
    }
}
