package org.edu_sharing.rendering.controller.external

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.edu_sharing.rendering.config.H5P_BASE_PATH
import org.edu_sharing.rendering.service.LumiNodeInfoService
import org.edu_sharing.rendering.service.LumiProxyService
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping(H5P_BASE_PATH)
class LumiProxyController(
    val lumiProxyService: LumiProxyService,
    val lumiNodeInfoService: LumiNodeInfoService
) {
    @GetMapping("/{contentId}")
    fun getContent(
        @PathVariable contentId: String,
        @RequestBody(required = false) body: String?,
        method: HttpMethod,
        request: HttpServletRequest,
        response: HttpServletResponse
    ): ResponseEntity<String> {
        return lumiProxyService.processProxyRequest(
            H5P_BASE_PATH,
            lumiNodeInfoService.getNodeInfo(contentId),
            body,
            method,
            request,
            response,
            UUID.randomUUID().toString(),
            String::class.java
        )
    }

    @GetMapping("/content/{contentId}/**")
    fun getContentAssets(
        @PathVariable contentId: String,
        @RequestBody(required = false) body: String?,
        method: HttpMethod,
        request: HttpServletRequest,
        response: HttpServletResponse
    ): ResponseEntity<ByteArray> {
        return lumiProxyService.processProxyRequest(
            H5P_BASE_PATH,
            lumiNodeInfoService.getNodeInfo(contentId),
            body,
            method,
            request,
            response,
            UUID.randomUUID().toString(),
            ByteArray::class.java
        )
    }

    @GetMapping("/**")
    fun getH5PCoreAssets(
        @RequestBody(required = false) body: String?,
        method: HttpMethod,
        request: HttpServletRequest,
        response: HttpServletResponse
    ): ResponseEntity<ByteArray> {
        return lumiProxyService.processProxyRequest(
            H5P_BASE_PATH,
            body,
            method,
            request,
            response,
            UUID.randomUUID().toString(),
            ByteArray::class.java
        )
    }
}
