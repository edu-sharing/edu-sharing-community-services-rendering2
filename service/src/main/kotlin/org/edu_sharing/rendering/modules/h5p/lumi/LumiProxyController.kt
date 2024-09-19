package org.edu_sharing.rendering.modules.h5p.lumi

import jakarta.servlet.http.HttpServletRequest
import org.edu_sharing.rendering.config.H5P_BASE_PATH
import org.edu_sharing.rendering.core.annotation.ConditionalOnController
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping(H5P_BASE_PATH)
@ConditionalOnController
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
    ): ResponseEntity<String> {
        return lumiProxyService.processProxyRequest(
            H5P_BASE_PATH,
            lumiNodeInfoService.getNodeInfo(contentId),
            body,
            method,
            request,
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
