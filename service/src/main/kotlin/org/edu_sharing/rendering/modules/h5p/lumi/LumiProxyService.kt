package org.edu_sharing.rendering.modules.h5p.lumi

import jakarta.servlet.http.HttpServletRequest
import org.apache.commons.lang3.StringUtils
import org.apache.logging.log4j.ThreadContext
import org.edu_sharing.rendering.core.exception.ResourceNotFoundException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.util.UriComponentsBuilder

@Service
class LumiProxyService(
    private val lumiWebClient: WebClient
) {
    @PreAuthorize("hasPermission(#nodeInfo.nodeId, 'Read')")
    fun <T> processProxyRequest(
        pathPrefix: String,
        nodeInfo: LumiNodeInfo,
        body: String?,
        method: HttpMethod,
        request: HttpServletRequest,
        traceId: String,
        responseType: Class<T>
    ): ResponseEntity<T> {
        return processProxyRequest(pathPrefix, body, method, request, traceId, responseType)
    }


    fun <T> processProxyRequest(
        pathPrefix: String,
        body: String?,
        method: HttpMethod,
        request: HttpServletRequest,
        traceId: String,
        responseType: Class<T>
    ): ResponseEntity<T> {
        ThreadContext.put("traceId", traceId)

        val requestURIPathSegments =
            request.requestURI.removePrefix(pathPrefix).split("/").stream().filter { StringUtils.isNotBlank(it) }
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
            .headers { headers }

        if (body != null) {
            lumiRequest.body(BodyInserters.fromValue(body))
        }

        return lumiRequest.retrieve()
            .toEntity(responseType)
            .block()
            ?: throw ResourceNotFoundException("Called lumi with ${method.name()} $requestURIPath ${request.queryString} $body")
    }
}
