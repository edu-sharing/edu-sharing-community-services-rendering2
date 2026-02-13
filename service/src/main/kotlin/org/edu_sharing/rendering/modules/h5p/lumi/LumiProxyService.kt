package org.edu_sharing.rendering.modules.h5p.lumi

import jakarta.servlet.http.HttpServletRequest
import org.apache.commons.lang3.StringUtils
import org.apache.logging.log4j.ThreadContext
import org.edu_sharing.rendering.core.annotation.ConditionalOnController
import org.edu_sharing.rendering.core.exception.ResourceNotFoundException
import org.edu_sharing.rendering.modules.h5p.lumi.dto.LumiNodeInfo
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.util.UriComponentsBuilder

@ConditionalOnController
@Service
class LumiProxyService(
    private val lumiWebClient: WebClient
) {
    @PreAuthorize("hasPermission(#nodeInfo.nodeId, 'ReadAll')")
    fun <T> processProxyRequest(
        pathPrefix: String,
        nodeInfo: LumiNodeInfo,
        body: String?,
        method: HttpMethod,
        request: HttpServletRequest,
        traceId: String,
        responseType: Class<T>,
        additionalHeaders: Map<String, String> = emptyMap()
    ): ResponseEntity<T> {
        return processProxyRequest(
            pathPrefix = pathPrefix,
            body = body,
            method = method,
            request = request,
            traceId = traceId,
            responseType = responseType,
            additionalHeaders = additionalHeaders
        )
    }

    fun <T> processProxyRequest(
        pathPrefix: String,
        body: String?,
        method: HttpMethod,
        request: HttpServletRequest,
        traceId: String,
        responseType: Class<T>,
        additionalHeaders: Map<String, String> = emptyMap()
    ): ResponseEntity<T> {
        ThreadContext.put("traceId", traceId)

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

        headers.set("TRACE", traceId)
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
        val lumiResponse = lumiRequest.retrieve()
            .toEntity(responseType)
            .block()
            ?: throw ResourceNotFoundException("Called lumi with ${method.name()} $requestURIPath ${request.queryString} $body")
        val responseHeaders = HttpHeaders()
        responseHeaders.addAll(lumiResponse.headers)
        responseHeaders.set("TRACE", traceId)
        additionalHeaders.forEach { (key, value) -> responseHeaders.set(key, value) }

        return ResponseEntity
            .status(lumiResponse.statusCode)
            .headers(responseHeaders)
            .body(lumiResponse.body)
    }
}
