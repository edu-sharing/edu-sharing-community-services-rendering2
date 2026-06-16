package org.edu_sharing.rendering.modules.h5p.lumi

import jakarta.servlet.http.HttpServletRequest
import org.edu_sharing.rendering.config.H5P_BASE_PATH
import org.edu_sharing.rendering.core.annotation.ConditionalOnController
import org.edu_sharing.rendering.modules.h5p.H5pRenderModule
import org.slf4j.LoggerFactory
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping(H5P_BASE_PATH)
@ConditionalOnController
class LumiProxyController(
    val lumiProxyService: LumiProxyService,
    val lumiContentManagementService: LumiContentManagementService,
    val module: H5pRenderModule
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping("/{contentId}")
    fun getContent(
        @PathVariable contentId: String,
        @RequestBody(required = false) body: String?,
        method: HttpMethod,
        request: HttpServletRequest,
    ): ResponseEntity<String> {
        log.debug("Proxy getContent: contentId={}, path={}", contentId, request.requestURI)
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
        log.debug("Proxy getContentAssets: contentId={}, path={}", contentId, request.requestURI)
        return lumiProxyService.processProxyRequest(
            H5P_BASE_PATH,
            lumiContentManagementService.getNodeInfo(contentId),
            body,
            method,
            request,
            ByteArray::class.java
        )
    }

    @GetMapping("/**")
    fun getH5PCoreAssets(
        @RequestBody(required = false) body: String?,
        method: HttpMethod,
        request: HttpServletRequest,
    ): ResponseEntity<ByteArray> {
        log.debug("Proxy getH5PCoreAssets: path={}", request.requestURI)
        return lumiProxyService.processProxyRequest(
            H5P_BASE_PATH,
            body,
            method,
            request,
            ByteArray::class.java
        )
    }
}
