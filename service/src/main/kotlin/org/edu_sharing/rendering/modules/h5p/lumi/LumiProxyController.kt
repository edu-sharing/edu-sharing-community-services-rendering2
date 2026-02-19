package org.edu_sharing.rendering.modules.h5p.lumi

import jakarta.servlet.http.HttpServletRequest
import org.edu_sharing.rendering.config.H5P_BASE_PATH
import org.edu_sharing.rendering.core.annotation.ConditionalOnController
import org.edu_sharing.rendering.modules.h5p.H5pRenderModule
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping(H5P_BASE_PATH)
@ConditionalOnController
class LumiProxyController(
    val lumiProxyService: LumiProxyService,
    val lumiContentManagementService: LumiContentManagementService,
    val module: H5pRenderModule
) {
    @GetMapping("/{contentId}")
    fun getContent(
        @PathVariable contentId: String,
        @RequestBody(required = false) body: String?,
        method: HttpMethod,
        request: HttpServletRequest,
    ): ResponseEntity<String> {
        val nodeInfo = lumiContentManagementService.getNodeInfo(contentId)
        val cspHeader = lumiContentManagementService.getCspHeader(nodeInfo.nodeId)
        val additionalHeader = cspHeader?.let {
            mapOf("Content-Security-Policy" to it)
        }
        val result =  lumiProxyService.processProxyRequest(
            pathPrefix = H5P_BASE_PATH,
            nodeInfo = nodeInfo,
            body = body,
            method = method,
            request = request,
            traceId = UUID.randomUUID().toString(),
            responseType = String::class.java,
            additionalHeaders = additionalHeader ?: emptyMap()
        )
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
            lumiContentManagementService.getNodeInfo(contentId),
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
