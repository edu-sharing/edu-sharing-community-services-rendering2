package org.edu_sharing.rendering.modules.h5p.lumi

import jakarta.servlet.http.HttpServletRequest
import org.edu_sharing.rendering.config.H5P_BASE_PATH
import org.edu_sharing.rendering.core.annotation.ConditionalOnController
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping(H5P_BASE_PATH)
@ConditionalOnController
class LumiProxyController(
    val lumiProxyService: LumiProxyService,
    val lumiNodeInfoService: LumiNodeInfoService,
    @Value("\${app.asset.static.frameAncestors}")
    private val allowedFrameAncestors: String?
) {
    @GetMapping("/{contentId}")
    fun getContent(
        @PathVariable contentId: String,
        @RequestBody(required = false) body: String?,
        method: HttpMethod,
        request: HttpServletRequest,
    ): ResponseEntity<String> {
        val result =  lumiProxyService.processProxyRequest(
            pathPrefix = H5P_BASE_PATH,
            nodeInfo = lumiNodeInfoService.getNodeInfo(contentId),
            body = body,
            method = method,
            request = request,
            traceId = UUID.randomUUID().toString(),
            responseType = String::class.java,
        )
        if (! allowedFrameAncestors.isNullOrBlank()) {
            val headers = HttpHeaders()
            headers.addAll(result.headers)
            headers.add("Content-Security-Policy", "frame-ancestors $allowedFrameAncestors" )
            return ResponseEntity
                .status(HttpStatus.OK)
                .headers(headers)
                .body(result.body)
        }
        return result
    }

    @GetMapping("/content/{contentId}/**")
    fun getContentAssets(
        @PathVariable contentId: String,
        @RequestBody(required = false) body: String?,
        method: HttpMethod,
        request: HttpServletRequest,
    ): ResponseEntity<ByteArray> {
        return lumiProxyService.processProxyRequest(
            H5P_BASE_PATH,
            lumiNodeInfoService.getNodeInfo(contentId),
            body,
            method,
            request,
            UUID.randomUUID().toString(),
            ByteArray::class.java
        )
    }

    @GetMapping("/**")
    fun getH5PCoreAssets(
        @RequestBody(required = false) body: String?,
        method: HttpMethod,
        request: HttpServletRequest,
    ): ResponseEntity<ByteArray> {
        return lumiProxyService.processProxyRequest(
            H5P_BASE_PATH,
            body,
            method,
            request,
            UUID.randomUUID().toString(),
            ByteArray::class.java
        )
    }
}
