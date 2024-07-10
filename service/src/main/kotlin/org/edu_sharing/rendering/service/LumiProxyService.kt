package org.edu_sharing.rendering.service

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.apache.commons.lang3.StringUtils
import org.apache.logging.log4j.ThreadContext
import org.edu_sharing.rendering.exception.ResourceNotFoundException
import org.edu_sharing.rendering.processing.h5p.LumiNodeInfo
import org.edu_sharing.rendering.processing.moodle.MoodleReceiver
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.util.UriComponentsBuilder

@Service
class LumiProxyService(
    private val lumiWebClient: WebClient
) {
    private val log = LoggerFactory.getLogger(MoodleReceiver::class.java)

    @PreAuthorize("hasPermission(#nodeInfo.nodeId, 'Read')")
    fun <T> processProxyRequest(
        pathPrefix: String,
        nodeInfo: LumiNodeInfo,
        body: String?,
        method: HttpMethod,
        request: HttpServletRequest,
        response: HttpServletResponse,
        traceId: String,
        responseType: Class<T>
    ): ResponseEntity<T> {
        return processProxyRequest(pathPrefix, body, method, request, response, traceId, responseType)
    }


    fun <T> processProxyRequest(
        pathPrefix: String,
        body: String?,
        method: HttpMethod,
        request: HttpServletRequest,
        response: HttpServletResponse,
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
            lumiRequest.body(body, String::class.java)
        }

        return lumiRequest.retrieve()
            .toEntity(responseType)
            .block()
            ?: throw ResourceNotFoundException("Called lumi with ${method.name()} $requestURIPath ${request.queryString} $body")
    }
}
